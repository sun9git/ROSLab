/**
 *
 */
package roslab.model.general;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Peter Gebhard
 */
public class Library {

    List<Node> nodes;

    /**
     * @param nodes
     */
    public Library(List<Node> nodes) {
        this.nodes = nodes;
    }

    /**
     * @return the nodes
     */
    public List<Node> getNodes() {
        return nodes;
    }

    /**
     * @return the nodes
     */
    public List<Node> getNodesOfClass(Class<?> clazz) {
        List<Node> subset = new ArrayList<Node>();
        for (Node n : nodes) {
            if (n.getClass().equals(clazz)) {
                subset.add(n);
            }
        }
        return subset;
    }

    /**
     * @param nodes
     *            the nodes to set
     */
    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    /**
     * @param node
     *            the node to add
     */
    public void addNode(Node node) {
        this.nodes.add(node);
    }

}
