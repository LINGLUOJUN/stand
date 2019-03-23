package net.dloud.platform.dal.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author QuDasheng
 * @create 2018-09-11 14:40
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InfoMethodSimple {
    private String clazzName;

    private String simpleName;

    private String invokeName;

    private Integer invokeLength;

    private String simpleComment;
}
