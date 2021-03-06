package com.tqmall.search.db.param;

/**
 * Created by xing on 16/4/5.
 * 直接查询所有列, 即"SELECT * FROM XXX"
 *
 * @author xing
 */
public class AllQueryParam extends QueryParam {

    public AllQueryParam(String schema, String table, int size) {
        super(schema, table, size);
    }

    @Override
    protected void appendSqlStatementOfFields(StringBuilder sql) {
        sql.append('*');
    }
}
