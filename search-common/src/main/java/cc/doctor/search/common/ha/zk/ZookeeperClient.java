package cc.doctor.search.common.ha.zk;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by doctor on 2017/3/12.
 */
public class ZookeeperClient {
    private Logger log = LoggerFactory.getLogger(ZookeeperClient.class);
    private int sessionTimeout = 10000;
    private String connectionString;
    private ZooKeeper zk = null;
    private ZookeeperWatcher zookeeperWatcher;
    private static Map<String, ZookeeperClient> zkClients = new ConcurrentHashMap<>();

    public ZookeeperClient(String connectionString, int sessionTimeout, ZookeeperWatcher zookeeperWatcher) {
        this.zookeeperWatcher = zookeeperWatcher;
        this.connectionString = connectionString;
        this.sessionTimeout = sessionTimeout;
        try {
            if (this.zookeeperWatcher == null) {
                this.zookeeperWatcher = new ZookeeperWatcher(this);
            }
            zk = new ZooKeeper(connectionString, sessionTimeout, this.zookeeperWatcher);
        } catch (Exception e) {
            log.error("", e);
        }
    }

    public ZookeeperClient(String connectionString, int sessionTimeout) {
        this(connectionString, sessionTimeout, null);
    }

    public ZookeeperWatcher getZookeeperWatcher() {
        return zookeeperWatcher;
    }

    public void releaseConnection() {
        if (zk != null) {
            try {
                this.zk.close();
            } catch (InterruptedException ignored) {
            }
        }
    }

    //check and watch path
    public boolean existsNode(String path) {
        try {
            Stat stat = zk.exists(path, true);
            return stat != null;
        } catch (KeeperException | InterruptedException e) {
            log.error("", e);
        }
        return false;
    }

    /**
     * CreateMode:
     * PERSISTENT (持续的，相对于EPHEMERAL，不会随着client的断开而消失)
     * PERSISTENT_SEQUENTIAL（持久的且带顺序的）
     * EPHEMERAL (短暂的，生命周期依赖于client session)
     * EPHEMERAL_SEQUENTIAL  (短暂的，带顺序的)
     */
    public boolean createPath(String path, String data) {
        try {
            this.zk.create(path, data == null ? null : data.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        } catch (KeeperException | InterruptedException e) {
            log.error("", e);
            return false;
        }
        return true;
    }

    public boolean createPathRecursion(String path, String data) {
        Stack<String> stack = new Stack<>();
        getCreatePathRecursion(stack, path);
        while (!stack.empty()) {
            String pop = stack.pop();
            boolean created;
            if (pop.equals(path)) {
                created = createPath(pop, data);
            } else {
                created = createPath(pop, null);
            }
            if (!created) {
                return false;
            }
        }
        return true;
    }

    public void getCreatePathRecursion(Stack<String> paths, String path) {
        paths.push(path);
        String parent = getParent(path);
        if (parent != null && !parent.equals("") && !existsNode(parent)) {
            getCreatePathRecursion(paths, parent);
        } else {
            return;
        }
    }

    private String getParent(String path) {
        if (path == null) {
            return null;
        }
        if (path.equals("/")) {
            return "/";
        }
        return path.substring(0, path.lastIndexOf('/'));
    }

    //read and watch path
    public String readData(String path) {
        try {
            byte[] data = this.zk.getData(path, true, null);
            if (data != null) {
                return new String(data);
            }
        } catch (KeeperException | InterruptedException e) {
            log.error("", e);
        }
        return null;
    }

    public boolean writeData(String path, String data) {
        try {
            Stat stat = this.zk.setData(path, data.getBytes(), -1);
            return stat != null;
        } catch (KeeperException | InterruptedException e) {
            log.error("", e);
        }
        return false;
    }

    public void deleteNode(String path) {
        try {
            this.zk.delete(path, -1);
        } catch (KeeperException | InterruptedException e) {
            log.error("", e);
        }
    }

    public static ZookeeperClient getClient(String connString) {
        if (zkClients.get(connString) == null) {
            ZookeeperClient zookeeperClient = new ZookeeperClient(connString, 10000);
            zkClients.put(connString, zookeeperClient);
        }
        return zkClients.get(connString);
    }

    public Map<String, String> getChildren(String parent) {
        Map<String, String> childDataMap = new LinkedHashMap<>();
        try {
            List<String> children = zk.getChildren(parent, true);
            for (String child : children) {
                String data = readData(parent + "/" + child);
                childDataMap.put(child, data);
            }
            return childDataMap;
        } catch (KeeperException | InterruptedException e) {
            log.error("", e);
        }
        return null;
    }
}
