package cc.doctor.search.store.indices.indexer;

import cc.doctor.search.store.indices.inverted.InvertedTable;
import cc.doctor.search.store.indices.inverted.WordInfo;
import cc.doctor.search.common.entity.Range;
import cc.doctor.search.common.schema.Schema;
import cc.doctor.search.store.indices.inverted.InvertedFile;
import cc.doctor.search.common.utils.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by doctor on 2017/3/3.
 */
public abstract class AbstractIndexer {
    protected Schema schema;
    protected InvertedFile invertedFile;
    protected IndexerService indexerService;

    public AbstractIndexer(IndexerService indexerService) {
        this.indexerService = indexerService;
        this.schema = indexerService.getSchema();
        this.invertedFile = indexerService.getInvertedFile();
    }

    public void insertWord(String docId, String field, Object value) {
        if (field == null || value == null) {
            return;
        }
        WordInfo wordInfoInner = getWordInfoInner(field, value);
        if (wordInfoInner == null) {    //词不存在,则创建新的倒排空间
            insertWordInner(CollectionUtils.list(docId), field, value);
        } else {    //词已经存在,更新原来的倒排空间
            updateInvertedDocs(CollectionUtils.list(docId), wordInfoInner);
        }
    }

    /**
     * insert word base the inverted doc
     * @param field field
     * @param valueDocMap <value,docId> map
     */
    public boolean insertWord(String field, Map<Object, Set<String>> valueDocMap) {
        for (Object value : valueDocMap.keySet()) {
            WordInfo wordInfoInner = getWordInfoInner(field, value);
            if (wordInfoInner == null) {    //词不存在,则创建新的倒排空间
                insertWordInner(valueDocMap.get(value), field, value);
            } else {    //词已经存在,更新原来的倒排空间
                updateInvertedDocs(valueDocMap.get(value), wordInfoInner);
            }
        }
        return true;
    }

    protected void updateInvertedDocs(Collection<String> docIds, WordInfo wordInfo) {
        InvertedTable invertedTable = invertedFile.getInvertedTable(wordInfo);
        for (String docId : docIds) {

            InvertedTable.InvertedDoc invertedDoc = invertedTable.getInvertedDoc(docId);
            if (invertedDoc != null) {
                invertedDoc.setDocFrequency(invertedDoc.getDocFrequency() + 1);
            } else {
                invertedTable.addInvertedDoc(new InvertedTable.InvertedDoc(docId, 1));
            }
        }
        invertedFile.writeInvertedTable(invertedTable);
        wordInfo.setPosition(invertedTable.getWordInfo().getPosition());
        wordInfo.setVersion(invertedTable.getWordInfo().getVersion());
    }

    //在索引增加一个词
    public abstract void insertWordInner(Collection<String> docIds, String field, Object value);
    //从索引删除一个词
    public abstract void deleteWord(Schema schema, String property, Object word);

    //获取索引的倒排信息,等值查询
    public abstract WordInfo getWordInfoInner(String field, Object value);

    //大于查询
    public abstract List<WordInfo> getWordInfoGreatThanInner(String field, Object value);

    //大于等于查询
    public abstract List<WordInfo> getWordInfoGreatThanEqualInner(String field, Object value);

    //小于查询
    public abstract List<WordInfo> getWordInfoLessThanInner(String field, Object value);

    //小于等于查询
    public abstract List<WordInfo> getWordInfoLessThanEqualInner(String field, Object value);

    public abstract List<WordInfo> getWordInfoRangeInner(String field, Range range);

    //前缀查询
    public abstract List<WordInfo> getWordInfoPrefixInner(String field, Object value);

    //分词查询
    public abstract List<WordInfo> getWordInfoMatchInner(String field, Object value);

    public abstract void writeLock();

    public abstract void unlock();

}
