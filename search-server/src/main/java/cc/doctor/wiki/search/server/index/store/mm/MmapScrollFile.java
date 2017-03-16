package cc.doctor.wiki.search.server.index.store.mm;

import cc.doctor.wiki.common.Tuple;
import cc.doctor.wiki.exceptions.file.FileException;
import cc.doctor.wiki.utils.FileUtils;
import cc.doctor.wiki.utils.SerializeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

import static cc.doctor.wiki.search.server.index.store.mm.ScrollFile.DateScrollFileNameStrategy.dateScrollFileNameStrategy;

/**
 * Created by doctor on 2017/3/16.
 * 两个文件指针,一个写,一个读
 */
public class MmapScrollFile implements ScrollFile {
    private static final Logger log = LoggerFactory.getLogger(ScrollFile.class);
    private String root;
    private int scrollSize;
    private MmapFile mmapFile;
    private MmapFile readFile;
    private List<String> files = new LinkedList<>();
    private long position = 0;
    private String current;
    private ScrollFileNameStrategy scrollFileNameStrategy;

    public MmapScrollFile(String root, int scrollSize) {
        this(root, scrollSize, dateScrollFileNameStrategy);
    }

    public MmapScrollFile(String root, int scrollSize, ScrollFileNameStrategy scrollFileNameStrategy) {
        this(root, scrollSize, scrollFileNameStrategy, 0);
    }

    public MmapScrollFile(String root, int scrollSize, ScrollFileNameStrategy scrollFileNameStrategy, long position) {
        this.root = root;
        this.scrollFileNameStrategy = scrollFileNameStrategy;
        this.scrollSize = scrollSize;
        files = FileUtils.list(root, "checkpoint");
        String file = getFile(position);
        current = file;
        try {
            if (file == null) {
                current = scrollFileNameStrategy.first();
                String currentFileAbsolute = root + "/" + current;
                if (!FileUtils.exists(currentFileAbsolute)) {
                    FileUtils.createFileRecursion(currentFileAbsolute);
                }
                readFile = mmapFile = new MmapFile(currentFileAbsolute, scrollSize);
            } else {
                int filePosition = (int) (position % scrollSize);
                mmapFile = new MmapFile(root + "/" + current, scrollSize, filePosition);
            }
        } catch (IOException e) {
            log.error("", e);
        }
    }

    @Override
    public List<String> files() {
        return files;
    }

    @Override
    public long position() {
        return position;
    }

    @Override
    public String getFile(long position) {
        int index = (int) (position / scrollSize);
        if (index >= files.size()) {
            return null;
        }
        return files.get(index);
    }

    @Override
    public String current() {
        return current;
    }

    @Override
    public String next() {
        return scrollFileNameStrategy.next(current);
    }

    @Override
    public <T extends Serializable> long writeSerializable(T serializable) {
        long start = position;
        try {
            byte[] bytes = SerializeUtils.serialize(serializable);
            if ((position % scrollSize + 4 + bytes.length) > scrollSize) {
                String nextFile = next();
                mmapFile.commit();
                mmapFile.clean();
                String nextFileAbsolute = root + "/" + nextFile;
                if (!FileUtils.exists(nextFileAbsolute)) {
                    FileUtils.createFileRecursion(nextFileAbsolute);
                }
                mmapFile = new MmapFile(nextFileAbsolute, scrollSize);
                current = nextFile;
                position = files.size() * scrollSize;
                files.add(current);
            }
            mmapFile.appendInt(bytes.length);
            mmapFile.appendBytes(bytes);
            start = position;
            position += bytes.length;
        } catch (IOException e) {
            log.error("", e);
        }
        return start;
    }

    @Override
    public <T extends Serializable> Tuple<Long, T> readSerializable(long position) {
        int positionInFile = (int) (position % scrollSize);
        String file = getFile(position);
        if (file == null) {
            throw new FileException("Position over limit.");
        }
        MmapFile rFile;
        //不在当前写文件里,则去读文件里查找
        if (!file.equals(current)) {
            if (!readFile.getFile().getName().equals(file)) {
                try {
                    readFile = new MmapFile(root + "/" + file, scrollSize);
                } catch (IOException e) {
                    log.error("", e);
                }
            }
            rFile = readFile;
        } else {
            rFile = mmapFile;
        }
        int size = rFile.readInt(positionInFile);
        return new Tuple<>(position + 4 + size, rFile.<T>readObject(positionInFile + 4, size));
    }
}