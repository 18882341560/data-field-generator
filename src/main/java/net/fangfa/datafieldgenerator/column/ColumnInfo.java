package net.fangfa.datafieldgenerator.column;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * @author gl
 * @version 1.0
 * @date 2019-03-13
 * @description: 类描述: 列信息
 **/
@Getter
@Setter
@Builder
@ToString
public class ColumnInfo {
    /**
     * 允许为 null
     */
    public static final String NULLABLE = "1";
    /**
     * 不允许为 null
     */
    public static final String NOT_NULLABLE = "0";
    /**
     * 表名
     */
    private String tableName;
    /**
     * 列名
     */
    private String columnName;
    /**
     * 列序号
     */
    private Integer columnOrdinal;
    /**
     * 默认值
     */
    private String columnDefault;
    /**
     * 是否可以为 null:1.可以;0:不可以
     */
    private Integer isNullable;
    /**
     * 数据类型
     */
    private String dataType;
    /**
     * 数据长度
     */
    private Integer dataMaxLength;
    /**
     * 注释
     */
    private String columnComment;
}
