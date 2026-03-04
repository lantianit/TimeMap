package com.timemap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.timemap.model.dto.NearbyPhotoResponse;
import com.timemap.model.entity.Photo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PhotoMapper extends BaseMapper<Photo> {

    @Select("""
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
        HAVING distance <= #{radiusKm}
        ORDER BY distance
        LIMIT 200
    """)
    List<NearbyPhotoResponse> findNearby(double lat, double lng, double radiusKm);
}
