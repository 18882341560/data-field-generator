package net.fangfa.datafieldgenerator;

import com.google.common.collect.Lists;
import gl.tool.util.lang.StrUtil;
import net.fangfa.datafieldgenerator.column.ColumnInfo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static net.fangfa.datafieldgenerator.column.DataTypeEnum.*;

/**
 * @author gl
 * @version 1.0
 * @date 2018/10/25
 * @description 类说明：简单的根据table生成java字段，mybatis的resultMap等,一个一个的敲也是个巨大工作量啊 映射规则，将有下划线的列名转换成驼峰结构，如user_name->userName
 */
public class DataFieldGenerator {

    public static void main(String[] args) throws SQLException {
        String tableSchema = "springbootdemo";
        String tableName = "cost_data";
        // String url = "jdbc:mysql://127.0.0.1:3306/" + tableSchema + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false";
        String url = "jdbc:sqlserver://192.168.0.99:1433;database=car";
        String username = "sqm";
        String password = "sqm8848007";
        DataFieldGenerator dataFieldGenerator = new DataFieldGenerator(tableSchema,tableName,url,username,password);
        System.out.println(dataFieldGenerator.getTotalString());
    }

    /**
     * 数据库类型:mysql
     */
    private static final String MYSQL_DATABASE = "mysql";

    /**
     * 数据库类型:sql server
     */
    private static final String SQL_SERVER_DATABASE = "sqlserver";
    /**
     * 数据库
     */
    private String tableSchema;
    /**
     * 表名
     */
    private String tableName;
    /**
     * JDBC连接地址
     */
    private String url;
    /**
     * 用户名
     */
    private String username;
    /**
     * 密码
     */
    private String password;


    /**
     * jdbc 连接器
     */
    private Connection connection;

    public DataFieldGenerator(String tableSchema, String tableName, String url, String username, String password) {
        this.tableSchema = tableSchema;
        this.tableName = tableName;
        this.url = url;
        this.username = username;
        this.password = password;
    }

    /**
     * 获取所需字符串
     */
    public String getTotalString()throws SQLException {
        StringBuilder out = new StringBuilder();
        List<ColumnInfo> columnInfoList = getTableStructure();
        out.append(getJavaObjectString(columnInfoList))
                .append("\n")
                .append(getMapperString(columnInfoList));
        return out.toString();
    }

    /**
     * 获取表结构信息
     */
    private List<ColumnInfo> getTableStructure() throws SQLException {
        String query;
        String databaseType = getDatabaseTypeByUrl(url);
        if (Objects.equals(MYSQL_DATABASE, databaseType)) {
            query = "SELECT \n" +
                    "       TABLE_NAME                                                 AS table_name,\n" +
                    "       COLUMN_NAME                                                AS column_name,\n" +
                    "       ORDINAL_POSITION                                           AS column_ordinal,\n" +
                    "       `IFNULL`(COLUMN_DEFAULT, '')                               AS column_default,\n" +
                    "       CASE WHEN IS_NULLABLE = 'YES' then '1' else '' end         AS is_nullable,\n" +
                    "       DATA_TYPE                                                  AS data_type,\n" +
                    "       CHARACTER_MAXIMUM_LENGTH                                   AS data_max_length,\n" +
                    "       COLUMN_COMMENT                                             AS column_comment\n" +
                    "FROM information_schema.COLUMNS \n" +
                    "WHERE\n" +
                    "  TABLE_SCHEMA= '" + tableSchema + "'\n" +
                    "  AND TABLE_NAME = ?;\n";
        } else if (Objects.equals(SQL_SERVER_DATABASE, databaseType)) {
            query =
                    "SELECT \n" +
                            "    table_name = case when a.colorder = 1 then d.name else '' end,\n" +
                            "    column_name = a.name,\n" +
                            "    column_ordinal = a.colorder,\n" +
                            "    column_default = isnull(e.text, ''),\n" +
                            "    is_nullable = case when a.isnullable = 1 then 1 else 0 end,\n" +
                            "    data_type = b.name,\n" +
                            "    data_max_length = a.length,\n" +
                            "    column_comment =isnull(g.[value], ''),\n" +
                            "    data_max_value = COLUMNPROPERTY(a.id, a.name, 'PRECISION'),\n" +
                            "    data_decimal_digit = isnull(COLUMNPROPERTY(a.id, a.name, 'Scale'), 0),\n" +
                            "    primary_key = case\n" +
                            "                      when exists(SELECT 1\n" +
                            "                                  FROM sysobjects\n" +
                            "                                  where xtype = 'PK'\n" +
                            "                                    and name in (\n" +
                            "                                      SELECT name\n" +
                            "                                      FROM sysindexes\n" +
                            "                                      WHERE indid in (\n" +
                            "                                          SELECT indid\n" +
                            "                                          FROM sysindexkeys\n" +
                            "                                          WHERE id = a.id\n" +
                            "                                            AND colid = a.colid\n" +
                            "                                      ))) then 1\n" +
                            "                      else 0 end,\n" +
                            "    is_identify = case when COLUMNPROPERTY(a.id, a.name, 'IsIdentity') = 1 then 1 else 0 end\n" +
                            "FROM syscolumns a\n" +
                            "         left join systypes b on a.xusertype = b.xusertype\n" +
                            "         inner join sysobjects d on a.id = d.id and d.xtype = 'U' and d.name <> 'dtproperties'\n" +
                            "         left join syscomments e on a.cdefault = e.id\n" +
                            "         left join sys.extended_properties g on a.id = g.major_id and a.colid = g.minor_id\n" +
                            "         left join sys.extended_properties f on d.id = f.major_id and f.minor_id = 0\n" +
                            "where \n" +
                            "   d.name = ?\n" +
                            "order by a.id, a.colorder\n";
        } else {
            throw new SQLException("未知的数据库类型");
        }
        List<ColumnInfo> list = Lists.newArrayList();
            PreparedStatement preparedStatement = getConnection().prepareStatement(query);
            preparedStatement.setString(1, tableName);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                ColumnInfo columnInfo = ColumnInfo.builder()
                        // .tableName(resultSet.getString("table_name"))
                        .tableName(resultSet.getString(1))
                        // .columnName(resultSet.getString("column_name"))
                        .columnName(resultSet.getString(2))
                        // .columnOrdinal(resultSet.getInt("column_ordinal"))
                        .columnOrdinal(resultSet.getInt(3))
                        // .columnDefault(resultSet.getString("column_default"))
                        .columnDefault(resultSet.getString(4))
                        // .isNullable(resultSet.getInt("is_nullable"))
                        .isNullable(resultSet.getInt(5))
                        // .dataType(resultSet.getString("data_type"))
                        .dataType(resultSet.getString(6))
                        // .dataMaxLength(resultSet.getInt("data_max_length"))
                        .dataMaxLength(resultSet.getInt(7))
                        // .columnComment(resultSet.getString("column_comment"))
                        .columnComment(resultSet.getString(8))
                        .build();
                list.add(columnInfo);
            }
        return list;
    }


    /**
     * 通过 url 获取数据库类型
     *
     * @param url
     * @return
     * @throws SQLException
     */
    private String getDatabaseTypeByUrl(String url) throws SQLException {
        if (url.contains(MYSQL_DATABASE)) {
            return MYSQL_DATABASE;
        }
        if (url.contains(SQL_SERVER_DATABASE)) {
            return SQL_SERVER_DATABASE;
        }
        throw new SQLException("未知JDBC连接地址");
    }

    /**
     * 获取 Mapper 文件 string
     *
     * @param columnInfoList
     * @return
     */
    public String getMapperString(List<ColumnInfo> columnInfoList) {
        StringBuilder out = new StringBuilder();
        StringBuilder resultMapString = new StringBuilder();
        StringBuilder columnsString = new StringBuilder();
        StringBuilder entitiesString = new StringBuilder();
        StringBuilder updateIfString = new StringBuilder();
        for (ColumnInfo columnInfo : columnInfoList) {
            resultMapString.append(getResultString(columnInfo));
            columnsString.append(getColumnString(columnInfo));
            entitiesString.append(getEntityString(columnInfo));
            updateIfString.append(getUpdateIfString(columnInfo));
        }
        /**
         * 删除逗号
         */
        columnsString.replace(columnsString.lastIndexOf(","), columnsString.lastIndexOf(",") + 1, "");
        entitiesString.replace(entitiesString.lastIndexOf(","), entitiesString.lastIndexOf(",") + 1, "");
        updateIfString.replace(updateIfString.lastIndexOf(","), updateIfString.lastIndexOf(",") + 1, "");
        out.append(resultMapString)
                .append("\n")
                .append(columnsString)
                .append("\n")
                .append(entitiesString)
                .append("\n")
                .append(updateIfString);
        return out.toString();
    }

    /**
     * 获取 mapper 文件的result字符串
     *
     * @param columnInfo
     * @return
     */
    private String getResultString(ColumnInfo columnInfo) {
        String columnName = columnInfo.getColumnName();
        String fieldName = getFieldNameByColumnName(columnName);
        return "        <result column=\"" + columnName
                + "\" property=\"" + fieldName
                + "\"/>\n";
    }

    /**
     * 获取 column 单行字符串
     *
     * @param columnInfo
     * @return
     */
    private String getColumnString(ColumnInfo columnInfo) {
        return "" + columnInfo.getColumnName() + ",\n";
    }

    /**
     * 获取 entity 单行字符串
     *
     * @param columnInfo
     * @return
     */
    private String getEntityString(ColumnInfo columnInfo) {
        return "#{" + getFieldNameByColumnName(columnInfo.getColumnName())
                + "},\n";
    }

    /**
     * 获取单行 update if 字符串
     *
     * @param columnInfo
     * @return
     */
    private String getUpdateIfString(ColumnInfo columnInfo) {
        String columnName = columnInfo.getColumnName();
        String fieldName = getFieldNameByColumnName(columnName);
        if ("id".equals(fieldName)
                || "createUser".equals(fieldName)
                || "createTime".equals(fieldName)) {
            return "";
        }
        String notEmpty = "";
        if (Objects.equals(VARCHAR.name(), columnInfo.getDataType().toUpperCase())) {
            notEmpty = " and " + fieldName + " != ''";
        }
        return "        <if test = \"" + fieldName + " != null" + notEmpty + "\">\n            "
                + columnName + " = #{" + fieldName + "},\n"
                + "        </if>\n";
    }

    /**
     * 获取 Java 对象字符串
     * @param columnInfoList
     * @return
     */
    public String getJavaObjectString(List<ColumnInfo> columnInfoList) {
        StringBuilder out = new StringBuilder();
        for (ColumnInfo columnInfo : columnInfoList) {
            out.append(getJavaFieldString(columnInfo));
        }
        return out.toString();
    }

    /**
     * 获取 java 对象字段字符串
     *
     * @param columnInfo
     */
    private String getJavaFieldString(ColumnInfo columnInfo) {
        StringBuilder out = new StringBuilder();
        String fieldType = getFieldTypeByDataType(columnInfo);
        String fieldName = getFieldNameByColumnName(columnInfo.getColumnName());
        out.append("/**\n")
                .append(" * ")
                .append(columnInfo.getColumnComment())
                .append("\n")
                .append(" */\n")
                .append("private ")
                .append(fieldType)
                .append(" ")
                .append(fieldName)
                .append(";\n");

        return out.toString();
    }

    /**
     * 通过列名获取 java 字段名
     *
     * @param columnName
     * @return
     */
    private String getFieldNameByColumnName(String columnName) {
        return StrUtil.convertUnderLineToLowCamelCase(columnName);
    }

    /**
     * 通过数据类型字符串获取 java 数据类型字符串
     *
     * @param columnInfo
     * @return
     */
    private String getFieldTypeByDataType(ColumnInfo columnInfo) {
        String dataType = columnInfo.getDataType().toUpperCase();
        String fieldType;
        if (Objects.equals(dataType, VARCHAR.name())) {
            fieldType = "String";
        } else if (Objects.equals(dataType, INT.name())) {
            fieldType = "Integer";
        } else if (Objects.equals(dataType, SMALLINT.name())) {
            fieldType = "Short";
        } else if (Objects.equals(dataType, BIGINT.name())) {
            fieldType = "Long";
        } else if (Objects.equals(dataType, TINYINT.name())) {
            fieldType = "Boolean";
        } else if (Objects.equals(dataType, DATETIME.name()) || Objects.equals(dataType, TIMESTAMP.name())) {
            fieldType = "LocalDateTime";
        } else if (Objects.equals(dataType, DATE.name())) {
            fieldType = "LocalDate";
        } else if (Objects.equals(dataType, DOUBLE.name())) {
            fieldType = "Double";
        } else {
            fieldType = "String";
        }
        return fieldType;
    }

    /**
     * 通过 Types 的 Int 值获取Java字段类型
     *
     * @param type
     * @param columnIndex
     * @return
     * @throws SQLException
     */
    private String getFieldTypeByTypesInt(int type, int columnIndex) throws SQLException {
        String fieldType;
        if (type == Types.VARCHAR) {
            fieldType = "String";
        } else if (type == Types.DECIMAL) {
            fieldType = "BigDecimal";
        } else if (type == Types.INTEGER) {
            fieldType = "Integer";
        } else if (type == Types.SMALLINT) {
            fieldType = "Short";
        } else if (type == Types.BIGINT) {
            fieldType = "Long";
        } else if (type == Types.TINYINT) {
            fieldType = "Boolean";
        } else if (type == Types.DATE || type == Types.TIMESTAMP) {
            fieldType = "LocalDateTime";
        } else if (type == Types.DOUBLE) {
            fieldType = "Double";
        } else {
            fieldType = "String";
        }
        return fieldType;
    }

    public Map<String, String> getColumnAndComment(String tableName) {
        String query = "SELECT COLUMN_NAME,COLUMN_COMMENT from information_schema.COLUMNS where TABLE_NAME=?";
        Map<String, String> map = new HashMap<>();
        try {
            PreparedStatement ps = getConnection().prepareStatement(query);
            ps.setString(1, tableName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {

                map.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    /**
     * 获取连接
     *
     * @return
     */
    private Connection getConnection() {
        if (connection == null) {
            try {
                connection = DriverManager
                        .getConnection(
                                this.url,
                                this.username,
                                this.password);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return connection;
    }
}
