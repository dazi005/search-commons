package com.tqmall.search.canal.handle;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.tqmall.search.canal.RowChangedData;
import com.tqmall.search.canal.action.SchemaTables;
import com.tqmall.search.canal.action.TableColumnCondition;
import com.tqmall.search.commons.lang.Function;
import com.tqmall.search.commons.utils.CommonsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Created by xing on 16/2/22.
 * 收集了每个schema.table{@link CanalInstanceHandle}
 *
 * @see #schemaTables
 */
public abstract class ActionableInstanceHandle<V> extends AbstractCanalInstanceHandle {

    private static final Logger log = LoggerFactory.getLogger(ActionableInstanceHandle.class);

    /**
     * 异常处理方法, 优先根据该Function处理
     */
    private Function<HandleExceptionContext, Boolean> handleExceptionFunction;
    /**
     * 是否忽略处理异常, 默认忽略
     * 优先处理{@link #handleExceptionFunction}
     */
    private boolean ignoreHandleException = true;

    protected final SchemaTables<V> schemaTables;

    private boolean userLocalTableFilter = true;

    /**
     * @param address      canal服务器地址
     * @param destination  canal实例名称
     * @param schemaTables table对应action实例
     */
    public ActionableInstanceHandle(SocketAddress address, String destination, SchemaTables<V> schemaTables) {
        super(address, destination);
        this.schemaTables = schemaTables;
    }

    protected abstract HandleExceptionContext buildHandleExceptionContext(RuntimeException exception);

    @Override
    protected void doConnect() {
        canalConnector.connect();
        if (userLocalTableFilter) {
            StringBuilder sb = new StringBuilder();
            for (SchemaTables.Schema<V> s : schemaTables) {
                String schemaName = s.getSchemaName();
                for (SchemaTables.Table t : s) {
                    sb.append(schemaName).append('.').append(t.getTableName()).append(',');
                }
            }
            sb.deleteCharAt(sb.length() - 1);
            canalConnector.subscribe(sb.toString());
        } else {
            canalConnector.subscribe();
        }
    }

    /**
     * {@link SchemaTables.Table#columns}有值, 则对于UPDATE操作过滤更改的字段是否包含在{@link SchemaTables.Table#columns}
     * 对于INSERT类型的记录更新, 如果条件判断没有通过, 可以认为该更新事件没有发生~~~~
     * 对于DELETE类型的记录更新, 如果条件判断没有通过, 可以认为该数据删除之前就不关心, 那这次删除我们更不关心了~~~
     *
     * @param rowChange 更改的数据
     * @return 解析结果
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    protected List<RowChangedData> changedDataParse(CanalEntry.RowChange rowChange) {
        SchemaTables.Table table = schemaTables.getTable(currentHandleSchema, currentHandleTable);
        List<RowChangedData> dataList;
        Set<String> columns;
        if (currentEventType == CanalEntry.EventType.UPDATE && (columns = table.getColumns()) != null) {
            dataList = new ArrayList<>();
            Iterator<CanalEntry.RowData> it = rowChange.getRowDatasList().iterator();
            next:
            while (it.hasNext()) {
                CanalEntry.RowData rowData = it.next();
                List<CanalEntry.Column> columnList = rowData.getAfterColumnsList();
                for (String c : columns) {
                    for (CanalEntry.Column ce : columnList) {
                        if (ce.getName().equals(c) && ce.getUpdated()) {
                            RowChangedData.Update update = RowChangedData.Update.CONVERT.apply(rowData);
                            if (update != null) dataList.add(update);
                            break next;
                        }
                    }
                }
            }
        } else {
            dataList = super.changedDataParse(rowChange);
        }
        if (CommonsUtils.isEmpty(dataList)) return null;
        if (currentEventType != CanalEntry.EventType.UPDATE && table.getColumnCondition() != null) {
            //对于INSERT类型的记录更新, 如果条件判断没有通过, 可以认为该更新事件没有发生~~~~
            //对于DELETE类型的记录更新, 如果条件判断没有通过, 可以认为该数据删除之前就不关心, 那这次删除我们更不关心了~~~
            TableColumnCondition columnCondition = table.getColumnCondition();
            Iterator<RowChangedData> it = dataList.iterator();
            while (it.hasNext()) {
                if (!columnCondition.validation(it.next())) {
                    it.remove();
                }
            }
        }
        return dataList;
    }

    @Override
    protected boolean exceptionHandle(RuntimeException exception, boolean inFinishHandle) {
        HandleExceptionContext context = buildHandleExceptionContext(exception);
        if (handleExceptionFunction != null) {
            Boolean ignore = handleExceptionFunction.apply(context);
            return ignore == null ? false : ignore;
        } else {
            log.error("canal " + instanceName + " handle table data change occurring exception: " + context.getSchema()
                    + '.' + context.getTable() + ", eventType: " + context.getEventType() + ", changedData size: "
                    + context.getChangedData().size() + ", ignoreHandleException: " + ignoreHandleException + ", inFinishHandle: "
                    + inFinishHandle, exception);
            return ignoreHandleException;
        }
    }

    /**
     * 是否忽略处理异常, 默认忽略
     * 优先处理{@link #handleExceptionFunction}
     */
    public void setIgnoreHandleException(boolean ignoreHandleException) {
        this.ignoreHandleException = ignoreHandleException;
    }

    /**
     * 异常处理方法, 优先根据该Function处理
     *
     * @param handleExceptionFunction 该function的返回结果标识是否忽略该异常, 同{@link #ignoreHandleException}
     * @see #ignoreHandleException
     */
    public void setHandleExceptionFunction(Function<HandleExceptionContext, Boolean> handleExceptionFunction) {
        this.handleExceptionFunction = handleExceptionFunction;
    }

    /**
     * {@link #canalConnector}连接时, 需要执行订阅{@link CanalConnector#subscribe()} / {@link CanalConnector#subscribe(String)}
     * 该变量标识是否使用本地, 即在{@link #schemaTables}中注册的schema, table
     * 如果为true, 订阅时生成filter, 提交直接替换canal server服务端配置的filter信息
     * 如果为false, 以canal server服务端配置的filter信息为准
     * 默认为true, 使用本地的filter配置
     */
    public void setUserLocalTableFilter(boolean userLocalTableFilter) {
        this.userLocalTableFilter = userLocalTableFilter;
    }

    @Override
    public boolean startHandle(CanalEntry.Header header) {
        return super.startHandle(header) && schemaTables.getTable(currentHandleSchema, currentHandleTable) != null;
    }
}