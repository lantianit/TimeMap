package com.timemap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.timemap.model.dto.NearbyPhotoResponse;
import com.timemap.model.entity.Photo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PhotoMapper extends BaseMapper<Photo> {

    @Select("""
        <script>
        SELECT id, image_url, thumbnail_url, longitude, latitude,
               location_name, photo_date,
               (6371 * 2 * ASIN(SQRT(
                   POW(SIN(RADIANS(latitude - #{lat}) / 2), 2) +
                   COS(RADIANS(#{lat})) * COS(RADIANS(latitude)) *
                   POW(SIN(RADIANS(longitude - #{lng}) / 2), 2)
               ))) AS distance
        FROM t_photo
        WHERE deleted = 0
          AND latitude  BETWEEN #{lat} - (#{radiusKm} / 111.0) AND #{lat} + (#{radiusKm} / 111.0)
          AND longitude BETWEEN #{lng} - (#{radiusKm} / (111.0 * COS(RADIANS(#{lat})))) AND #{lng} + (#{radiusKm} / (111.0 * COS(RADIANS(#{lat}))))
          <if test="startDate != null and startDate != ''">
            AND photo_date &gt;= #{startDate}
          </if>
          <if test="endDate != null and endDate != ''">
            AND photo_date &lt;= #{endDate}
          </if>
        HAVING distance &lt;= #{radiusKm}
        ORDER BY distance
        LIMIT 200
        </script>
    """)
    List<NearbyPhotoResponse> findNearby(@Param("lat") double lat,
                                          @Param("lng") double lng,
                                          @Param("radiusKm") double radiusKm,
                                          @Param("startDate") String startDate,
                                          @Param("endDate") String endDate);
}
