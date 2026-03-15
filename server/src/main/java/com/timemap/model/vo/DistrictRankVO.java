package com.timemap.model.vo;

import lombok.Data;

@Data
public class DistrictRankVO {
    /** 区县名 */
    private String district;
    /** 所属城市 */
    private String city;
    /** 照片数 */
    private Long photoCount;
    /** 参与拍摄人数 */
    private Long userCount;
    /** 今日上传数 */
    private Long todayCount;
    /** 最新一张照片的缩略图 */
    private String latestThumbUrl;
    /** 排名（由 Service 层赋值） */
    private Integer rank;
}
