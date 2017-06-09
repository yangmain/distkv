package com.github.wenweihu86.distkv.meta;

import com.github.wenweihu86.distkv.api.CommonMessage;
import com.github.wenweihu86.distkv.api.MetaMessage;
import com.github.wenweihu86.raft.RaftOptions;
import com.github.wenweihu86.raft.StateMachine;
import org.apache.commons.io.FileUtils;
import org.rocksdb.Checkpoint;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * Created by wenweihu86 on 2017/6/8.
 */
public class MetaStateMachine implements StateMachine {

    private static final Logger LOG = LoggerFactory.getLogger(MetaStateMachine.class);

    static {
        RocksDB.loadLibrary();
    }

    private RocksDB db;

    @Override
    public void writeSnapshot(String snapshotDir) {
        Checkpoint checkpoint = Checkpoint.create(db);
        try {
            checkpoint.createCheckpoint(snapshotDir);
        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.warn("writeSnapshot meet exception, dir={}, msg={}",
                    snapshotDir, ex.getMessage());
        }
    }

    @Override
    public void readSnapshot(String snapshotDir) {
        try {
            // copy snapshot dir to data dir
            String dataDir = RaftOptions.dataDir + File.separator + "rocksdb_data";
            File dataFile = new File(dataDir);
            if (dataFile.exists()) {
                FileUtils.deleteDirectory(dataFile);
            }
            File snapshotFile = new File(snapshotDir);
            if (snapshotFile.exists()) {
                FileUtils.copyDirectory(snapshotFile, dataFile);
            }
            // open rocksdb data dir
            Options options = new Options();
            options.setCreateIfMissing(true);
            db = RocksDB.open(options, dataDir);
        } catch (Exception ex) {
            LOG.warn("meet exception, msg={}", ex.getMessage());
        }
    }

    @Override
    public void apply(byte[] dataBytes) {
        ByteBuffer keyByteBuffer = ByteBuffer.allocate(8);
        ByteBuffer valueByteBuffer = ByteBuffer.allocate(4);
        try {
            MetaMessage.SetRequest request = MetaMessage.SetRequest.parseFrom(dataBytes);
            keyByteBuffer.putLong(request.getKeySign());
            byte[] keyBytes = keyByteBuffer.array();
            valueByteBuffer.putInt(request.getGroupId());
            byte[] valueBytes = valueByteBuffer.array();
            db.put(keyBytes, valueBytes);
        } catch (Exception ex) {
            LOG.warn("apply meet exception: ", ex);
        }
    }

    public MetaMessage.GetResponse get(MetaMessage.GetRequest request) {
        MetaMessage.GetResponse.Builder responseBuilder = MetaMessage.GetResponse.newBuilder();
        CommonMessage.BaseResponse.Builder baseResBuilder = CommonMessage.BaseResponse.newBuilder();
        baseResBuilder.setResCode(CommonMessage.ResCode.RES_CODE_FAIL);
        ByteBuffer keyByteBuffer = ByteBuffer.allocate(8);
        try {
            keyByteBuffer.putLong(request.getKeySign());
            byte[] keyBytes = keyByteBuffer.array();
            byte[] valueBytes = db.get(keyBytes);
            if (valueBytes != null) {
                ByteBuffer valueByteBuffer = ByteBuffer.wrap(valueBytes);
                responseBuilder.setGroupId(valueByteBuffer.getInt());
                baseResBuilder.setResCode(CommonMessage.ResCode.RES_CODE_SUCCESS);
            }
            responseBuilder.setBaseRes(baseResBuilder);
            MetaMessage.GetResponse response = responseBuilder.build();
            return response;
        } catch (RocksDBException ex) {
            LOG.warn("read rockdb exception: ", ex);
            return null;
        }
    }

}