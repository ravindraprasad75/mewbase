package io.mewbase.server.impl;

import io.mewbase.bson.BsonObject;
import io.mewbase.common.FrameHandler;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.parsetools.RecordParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO we should write our own BSON parser and only decode fields when needed.
 * BSON is ordered and `type` should be the first field - we only need to decode that in order to know what to do
 * with the frame - in the case of a PUB we should be able to pass the buffer direct to subscribers without
 * further decoding of the event, also to storage. I.e. we want to avoid decoding the entire frame in all cases
 * <p>
 * Created by tim on 23/09/16.
 */
public class Protocol {

    public static final String FRAME_TYPE_FIELD = "type";
    public static final String FRAME_FRAME_FIELD = "frame";

    // Frame types

    public static final String RESPONSE_FRAME = "RESPONSE";
    public static final String CONNECT_FRAME = "CONNECT";
    public static final String PUBLISH_FRAME = "PUB";
    public static final String STARTTX_FRAME = "STARTTX";
    public static final String COMMITTX_FRAME = "COMMITTX";
    public static final String ABORTTX_FRAME = "ABORTTX";
    public static final String SUBSCRIBE_FRAME = "SUBSCRIBE";
    public static final String UNSUBSCRIBE_FRAME = "UNSUBSCRIBE";
    public static final String SUBCLOSE_FRAME = "SUBCLOSE";
    public static final String SUBRESPONSE_FRAME = "SUBRESPONSE";
    public static final String RECEV_FRAME = "RECEV";
    public static final String ACKEV_FRAME = "ACKEV";
    public static final String FINDBYID_FRAME = "FINDBYID";
    public static final String QUERY_FRAME = "QUERY";
    public static final String QUERYRESULT_FRAME = "QUERYRESULT";
    public static final String QUERYACK_FRAME = "QUERYACK";
    public static final String PING_FRAME = "PING";
    public static final String LIST_BINDERS_FRAME = "LISTBINDERS";
    public static final String CREATE_BINDER_FRAME = "CREATEBINDER";
    public static final String LIST_CHANNELS_FRAME = "LISTCHANNNELS";
    public static final String CREATE_CHANNEL_FRAME = "CREATECHANNEL";
    public static final String COMMAND_FRAME = "COMMAND";

    // Frame fields

    public static final String REQUEST_REQUEST_ID = "rID";

    public static final String RESPONSE_REQUEST_ID = "rID";
    public static final String RESPONSE_OK = "ok";
    public static final String RESPONSE_ERRMSG = "errMsg";
    public static final String RESPONSE_ERRCODE = "errCode";

    public static final String SUBRESPONSE_SUBID = "subID";

    public static final String CONNECT_AUTH_INFO = "authInfo";
    public static final String CONNECT_VERSION = "version";

    public static final String PUBLISH_CHANNEL = "channel";
    public static final String PUBLISH_EVENT = "event";
    public static final String PUBLISH_SESSID = "sessID";

    public static final String STARTTX_SESSID = "sessID";

    public static final String COMMITTX_SESSID = "sessID";

    public static final String ABORTTX_SESSID = "sessID";

    public static final String SUBSCRIBE_CHANNEL = "channel";
    public static final String SUBSCRIBE_STARTPOS = "startPos";
    public static final String SUBSCRIBE_STARTTIMESTAMP = "startTimestamp";
    public static final String SUBSCRIBE_DURABLEID = "durableID";
    public static final String SUBSCRIBE_MATCHER = "matcher";

    public static final String UNSUBSCRIBE_SUBID = "subID";

    public static final String SUBCLOSE_SUBID = "subID";

    public static final String RECEV_SUBID = "subID";
    public static final String RECEV_TIMESTAMP = "timestamp";
    public static final String RECEV_POS = "pos";
    public static final String RECEV_EVENT = "event";

    public static final String ACKEV_SUBID = "subID";
    public static final String ACKEV_BYTES = "bytes";
    public static final String ACKEV_POS = "pos";

    public static final String FINDBYID_BINDER = "binder";
    public static final String FINDBYID_DOCID = "dodID";

    public static final String FINDRESPONSE_RESULT = "result";

    public static final String QUERY_QUERYID = "queryID";
    public static final String QUERY_NAME = "name";
    public static final String QUERY_PARAMS = "params";

    public static final String QUERYRESULT_OK = "ok";
    public static final String QUERYRESULT_QUERYID = "queryID";
    public static final String QUERYRESULT_RESULT = "result";
    public static final String QUERYRESULT_LAST = "last";

    public static final String QUERYACK_QUERYID = "queryID";
    public static final String QUERYACK_BYTES = "bytes";

    // Admin fields
    public static final String LISTBINDERS_BINDERS = "binders";

    public static final String CREATEBINDER_NAME = "name";
    public static final String CREATEBINDER_RESPONSE_EXISTS = "exists";

    public static final String LISTCHANNELS_CHANNELS = "channels";
    public static final String CREATECHANNEL_NAME = "name";
    public static final String CREATECHANNEL_RESPONSE_EXISTS = "exists";

    // Command fields
    public static final String COMMAND_NAME = "name";
    public static final String COMMAND_COMMAND = "command";

    private final static Logger logger = LoggerFactory.getLogger(Protocol.class);

    private final FrameHandler frameHandler;
    private final RecordParser parser;

    public Protocol(FrameHandler frameHandler) {
        this.frameHandler = frameHandler;
        parser = RecordParser.newFixed(4, null);
        Handler<Buffer> handler = new Handler<Buffer>() {
            int size = -1;

            public void handle(Buffer buff) {
                if (size == -1) {
                    size = buff.getIntLE(0) - 4;
                    parser.fixedSizeMode(size);
                } else {
                    handleFrame(size, buff);
                    parser.fixedSizeMode(4);
                    size = -1;
                }
            }
        };
        parser.setOutput(handler);
    }

    public RecordParser recordParser() {
        return parser;
    }

    private void handleFrame(int size, Buffer buffer) {
        // TODO bit clunky - need to add size back in so it can be decoded, improve this!
        Buffer buff2 = Buffer.buffer(buffer.length() + 4);
        buff2.appendIntLE(size + 4).appendBuffer(buffer);
        BsonObject bson = new BsonObject(buff2);
        handleBson(size, bson);
    }

    private void handleBson(int size, BsonObject bson) {
        String type = bson.getString(FRAME_TYPE_FIELD);
        BsonObject frame = bson.getBsonObject(FRAME_FRAME_FIELD);
        switch (type) {
            case RESPONSE_FRAME:
                frameHandler.handleResponse(frame);
                break;
            case CONNECT_FRAME:
                frameHandler.handleConnect(frame);
                break;
            case PUBLISH_FRAME:
                frameHandler.handlePublish(frame);
                break;
            case STARTTX_FRAME:
                frameHandler.handleStartTx(frame);
                break;
            case COMMITTX_FRAME:
                frameHandler.handleCommitTx(frame);
                break;
            case ABORTTX_FRAME:
                frameHandler.handleAbortTx(frame);
                break;
            case SUBSCRIBE_FRAME:
                frameHandler.handleSubscribe(frame);
                break;
            case SUBCLOSE_FRAME:
                frameHandler.handleSubClose(frame);
                break;
            case UNSUBSCRIBE_FRAME:
                frameHandler.handleUnsubscribe(frame);
                break;
            case SUBRESPONSE_FRAME:
                frameHandler.handleSubResponse(frame);
                break;
            case RECEV_FRAME:
                frameHandler.handleRecev(size, frame);
                break;
            case ACKEV_FRAME:
                frameHandler.handleAckEv(frame);
                break;
            case FINDBYID_FRAME:
                frameHandler.handleFindByID(frame);
                break;
            case QUERY_FRAME:
                frameHandler.handleQuery(frame);
                break;
            case QUERYRESULT_FRAME:
                frameHandler.handleQueryResult(size, frame);
                break;
            case QUERYACK_FRAME:
                frameHandler.handleQueryAck(frame);
                break;
            case PING_FRAME:
                frameHandler.handlePing(frame);
                break;
            case LIST_BINDERS_FRAME:
                frameHandler.handleListBinders(frame);
                break;
            case CREATE_BINDER_FRAME:
                frameHandler.handleCreateBinder(frame);
                break;
            case LIST_CHANNELS_FRAME:
                frameHandler.handleListChannels(frame);
                break;
            case CREATE_CHANNEL_FRAME:
                frameHandler.handleCreateChannel(frame);
                break;
            case COMMAND_FRAME:
                frameHandler.handleCommand(frame);
                break;
            default:
                logger.error("Invalid frame type: " + type);
        }
    }

    public static Buffer encodeFrame(String frameType, BsonObject frame) {
        BsonObject env = new BsonObject();
        env.put(FRAME_TYPE_FIELD, frameType).put(FRAME_FRAME_FIELD, frame);
        return env.encode();
    }
}


