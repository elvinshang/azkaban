package azkaban.flow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Flow {
	public enum State {
		READY, RUNNING, RUNNING_WITH_FAILURE, FAILED, SUCCEEDED
	}
	private final String id;
	private ArrayList<Node> startNodes;
	private ArrayList<Node> endNodes;
	
	private HashMap<String, Node> nodes = new HashMap<String, Node>();

	private HashMap<String, Edge> edges = new HashMap<String, Edge>();
	private HashMap<String, Set<Edge>> outEdges = new HashMap<String, Set<Edge>>();
	private HashMap<String, Set<Edge>> inEdges = new HashMap<String, Set<Edge>>();
	private HashMap<String, FlowProps> flowProps = new HashMap<String, FlowProps>(); 

	private ArrayList<String> errors;

	private boolean isLayedOut = false;
	
	public Flow(String id) {
		this.id = id;
	}
	
	public void initialize() {
		if (startNodes == null) {
			startNodes = new ArrayList<Node>();
			endNodes = new ArrayList<Node>();
			for (Node node : nodes.values()) {
				// If it doesn't have any incoming edges, its a start node
				if (!inEdges.containsKey(node.getId())) {
					startNodes.add(node);
				}

				// If it doesn't contain any outgoing edges, its an end node.
				if (!outEdges.containsKey(node.getId())) {
					endNodes.add(node);
				}
			}
			
			for (Node node: startNodes) {
				node.setLevel(0);
				recursiveSetLevels(node);
			}
		}
	}

	private void recursiveSetLevels(Node node) {
		Set<Edge> edges = outEdges.get(node.getId());
		if (edges != null) {
			for (Edge edge : edges) {
				Node nextNode = nodes.get(edge.getTargetId());
				edge.setSource(node);
				edge.setTarget(nextNode);
				
				// We pick whichever is higher to get the max distance from root.
				int level = Math.max(node.getLevel() + 1, nextNode.getLevel());
				nextNode.setLevel(level);
				recursiveSetLevels(nextNode);
			}
		}
	}

	public List<Node> getStartNodes() {
		return startNodes;
	}
	
	public List<Node> getEndNodes() {
		return endNodes;
	}
	
	public Set<Edge> getInEdges(String id) {
		return inEdges.get(id);
	}
	
	public Set<Edge> getOutEdges(String id) {
		return outEdges.get(id);
	}
	
	public void addAllNodes(Collection<Node> nodes) {
		for (Node node: nodes) {
			addNode(node);
		}
	}

	public void addNode(Node node) {
		nodes.put(node.getId(), node);
	}

	public void addAllFlowProperties(Collection<FlowProps> props) {
		for (FlowProps prop : props) {
			flowProps.put(prop.getSource(), prop);
		}
	}

	public String getId() {
		return id;
	}

	public void addError(String error) {
		if (errors == null) {
			errors = new ArrayList<String>();
		}

		errors.add(error);
	}
	
	public List<String> getErrors() {
		return errors;
	}
	
	public boolean hasErrors() {
		return errors != null && !errors.isEmpty();
	}
	
	public Collection<Node> getNodes() {
		return nodes.values();
	}
	
	public Collection<Edge> getEdges() {
		return edges.values();
	}
	
	public void addAllEdges(Collection<Edge> edges) {
		for (Edge edge: edges) {
			addEdge(edge);
		}
	}

	public void addEdge(Edge edge) {
		String source = edge.getSourceId();
		String target = edge.getTargetId();
	
		if (edge.hasError()) {
			addError("Error on " + edge.getId() + ". " + edge.getError());
		}

		Set<Edge> sourceSet = getEdgeSet(outEdges, source);
		sourceSet.add(edge);
		
		Set<Edge> targetSet = getEdgeSet(inEdges, target);
		targetSet.add(edge);
		
		edges.put(edge.getId(), edge);
	}
	
	private Set<Edge> getEdgeSet(HashMap<String, Set<Edge>> map, String id) {
		Set<Edge> edges = map.get(id);
		if (edges == null) {
			edges = new HashSet<Edge>();
			map.put(id, edges);
		}
		
		return edges;
	}

	public Map<String,Object> toObject() {
		HashMap<String, Object> flowObj = new HashMap<String, Object>();
		flowObj.put("type", "flow");
		flowObj.put("id", getId());
		flowObj.put("props", objectizeProperties());
		flowObj.put("nodes", objectizeNodes());
		flowObj.put("edges", objectizeEdges());
		if (errors != null) {
			flowObj.put("errors", errors);
		}
		
		return flowObj;
	}
	
	private List<Object> objectizeProperties() {
		ArrayList<Object> result = new ArrayList<Object>();
		for (FlowProps props: flowProps.values()) {
			Object objProps = props.toObject();
			result.add(objProps);
		}
		
		return result;
	}

	private List<Object> objectizeNodes() {
		ArrayList<Object> result = new ArrayList<Object>();
		for (Node node : getNodes()) {
			Object nodeObj = node.toObject();
			result.add(nodeObj);
		}
		
		return result;
	}
	
	private List<Object> objectizeEdges() {
		ArrayList<Object> result = new ArrayList<Object>();
		for (Edge edge: getEdges()) {
			Object edgeObj = edge.toObject();
			result.add(edgeObj);
		}
		
		return result;
	}
	
	@SuppressWarnings("unchecked")
	public static Flow flowFromObject(Object object) {
		Map<String, Object> flowObject = (Map<String,Object>)object;
		
		String id = (String)flowObject.get("id");
		Flow flow = new Flow(id);
		
		// Loading projects
		List<Object> propertiesList = (List<Object>)flowObject.get("props");
		Map<String, FlowProps> properties = loadPropertiesFromObject(propertiesList);
		flow.addAllFlowProperties(properties.values());
		
		// Loading nodes
		List<Object> nodeList = (List<Object>)flowObject.get("nodes");
		Map<String, Node> nodes = loadNodesFromObjects(nodeList);
		flow.addAllNodes(nodes.values());
		
		// Loading edges
		List<Object> edgeList = (List<Object>)flowObject.get("edges");
		List<Edge> edges = loadEdgeFromObjects(edgeList, nodes);
		flow.addAllEdges(edges);
		
		return flow;
	}

	private static Map<String, Node> loadNodesFromObjects(List<Object> nodeList) {
		Map<String, Node> nodeMap = new HashMap<String, Node>();
		
		for (Object obj: nodeList) {
			Node node = Node.fromObject(obj);
			nodeMap.put(node.getId(), node);
		}
		
		return nodeMap;
	}

	private static List<Edge> loadEdgeFromObjects(List<Object> edgeList, Map<String, Node> nodes) {
		List<Edge> edgeResult = new ArrayList<Edge>();
		
		for (Object obj: edgeList) {
			Edge edge = Edge.fromObject(obj);
			edgeResult.add(edge);
		}
		
		return edgeResult;
	}

	private static Map<String, FlowProps> loadPropertiesFromObject(List<Object> propertyObjectList) {
		Map<String, FlowProps> properties = new HashMap<String, FlowProps>();

		for (Object propObj: propertyObjectList) {
			FlowProps prop = FlowProps.fromObject(propObj);
			properties.put(prop.getSource(), prop);
		}
		
		return properties;
	}

	public boolean isLayedOut() {
		return isLayedOut;
	}

	public void setLayedOut(boolean layedOut) {
		this.isLayedOut = layedOut;
	}

	/*package*/ Map<String, Node> getNodeMap() {
		return nodes;
	}
	
	/*package*/ Map<String, Set<Edge>> getOutEdgeMap() {
		return outEdges;
	}
	
	/*package*/ Map<String, Set<Edge>> getInEdgeMap() {
		return inEdges;
	}
}