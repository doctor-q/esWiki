package cc.doctor.wiki.search.client.rpc.result;

/**
 * Created by doctor on 2017/3/15.
 * 创建,删除索引,索引别名
 */
public class IndexResult extends RpcResult {
    private static final long serialVersionUID = 8672112077762678134L;
    String indexName;
    String alias;
}
