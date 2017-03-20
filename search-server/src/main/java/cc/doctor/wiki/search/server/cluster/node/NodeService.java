package cc.doctor.wiki.search.server.cluster.node;

import cc.doctor.wiki.ha.zk.ZookeeperClient;
import cc.doctor.wiki.search.server.cluster.routing.NodeState;
import cc.doctor.wiki.search.server.cluster.routing.RoutingNode;
import cc.doctor.wiki.search.server.common.config.GlobalConfig;
import cc.doctor.wiki.utils.StringUtils;

import static cc.doctor.wiki.search.server.common.config.Settings.settings;

/**
 * Created by doctor on 2017/3/20.
 */
public class NodeService {
    private ZookeeperClient zkClient = ZookeeperClient.getClient(settings.get(GlobalConfig.ZOOKEEPER_CONN_STRING));
    public static final String NODE_PATH = settings.get(GlobalConfig.ZOOKEEPER_NODE_PATH);
    private Node node;

    public NodeService(Node node) {
        this.node = node;
    }

    public void registerNode() {
        RoutingNode routingNode = new RoutingNode();
        routingNode.setNodeId(StringUtils.base64UUid());
        routingNode.setNodeName(settings.get(GlobalConfig.NODE_NAME));
        routingNode.setNodeState(NodeState.STARTING);
        String nodePath = NODE_PATH + "/" + routingNode.getNodeName();
        if (!zkClient.existsNode(nodePath)) {
            zkClient.createPathRecursion(nodePath, StringUtils.toNameValuePairString(routingNode));
        }
        zkClient.writeData(nodePath, StringUtils.toNameValuePairString(routingNode));
        node.setRoutingNode(routingNode);
    }

    public void unregisterNode() {
        zkClient.deleteNode(NODE_PATH + "/" + node.getRoutingNode().getNodeName());
    }
}
