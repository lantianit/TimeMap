package com.timemap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.timemap.model.dto.NearbyPhotoResponse;
import com.timemap.model.entity.Photo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.timemap.model.dto.CommunityPhotoResponse;
import java.util.List;

@Mapper
public interface PhotoMapper extends BaseMapper<Photo> {

    @Select("""
        <script>
        SELECT id, image_url, thumbnail_url, longitude, latitude,
               location_name, photo_date,
               (SELECT COUNT(*) FROM t_comment WHERE photo_id = t_photo.id AND deleted = 0) AS comment_count,
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

    @Select("""
        <script>
        SELECT p.id, p.user_id, p.image_url, p.thumbnail_url, p.longitude, p.latitude,
               p.location_name, p.photo_date, p.create_time, u.nickname, u.avatar_url
        FROM t_photo p
        LEFT JOIN t_user u ON p.user_id = u.id
        WHERE p.deleted = 0
          AND p.district = #{district}
        <if test="sortBy == 'createTime'">
        ORDER BY p.create_time DESC, p.id DESC
        </if>
        <if test="sortBy != 'createTime'">
        ORDER BY p.photo_date DESC, p.id DESC
        </if>
        LIMIT #{offset}, #{size}
        </script>
    """)
    List<CommunityPhotoResponse> findCommunity(@Param("offset") int offset,
                                                @Param("size") int size,
                                                @Param("sortBy") String sortBy,
                                                @Param("district") String district);

    @Select("SELECT COUNT(*) FROM t_photo WHERE deleted = 0 AND district = #{district}")
    long countCommunity(@Param("district") String district);

    @Select("""
        <script>
        SELECT COUNT(*) FROM t_photo
        WHERE deleted = 0
          AND latitude  BETWEEN #{lat} - (#{radiusKm} / 111.0) AND #{lat} + (#{radiusKm} / 111.0)
          AND longitude BETWEEN #{lng} - (#{radiusKm} / (111.0 * COS(RADIANS(#{lat})))) AND #{lng} + (#{radiusKm} / (111.0 * COS(RADIANS(#{lat}))))
          AND (6371 * 2 * ASIN(SQRT(
                   POW(SIN(RADIANS(latitude - #{lat}) / 2), 2) +
                   COS(RADIANS(#{lat})) * COS(RADIANS(latitude)) *
                   POW(SIN(RADIANS(longitude - #{lng}) / 2), 2)
               ))) &lt;= #{radiusKm}
        </script>
    """)
    long countNearby(@Param("lat") double lat,
                     @Param("lng") double lng,
                     @Param("radiusKm") double radiusKm);

    @Select("""
        <script>
        SELECT COUNT(*) FROM t_photo
        WHERE deleted = 0
          AND DATE(create_time) = CURDATE()
          AND latitude  BETWEEN #{lat} - (#{radiusKm} / 111.0) AND #{lat} + (#{radiusKm} / 111.0)
          AND longitude BETWEEN #{lng} - (#{radiusKm} / (111.0 * COS(RADIANS(#{lat})))) AND #{lng} + (#{radiusKm} / (111.0 * COS(RADIANS(#{lat}))))
          AND (6371 * 2 * ASIN(SQRT(
                   POW(SIN(RADIANS(latitude - #{lat}) / 2), 2) +
                   COS(RADIANS(#{lat})) * COS(RADIANS(latitude)) *
                   POW(SIN(RADIANS(longitude - #{lng}) / 2), 2)
               ))) &lt;= #{radiusKm}
        </script>
    """)
    long countToday(@Param("lat") double lat,
                    @Param("lng") double lng,
                    @Param("radiusKm") double radiusKm);

    @Select("SELECT COUNT(*) FROM t_photo WHERE deleted = 0 AND district = #{district}")
    long countByDistrict(@Param("district") String district);

    @Select("SELECT COUNT(*) FROM t_photo WHERE deleted = 0 AND district = #{district} AND DATE(create_time) = CURDATE()")
    long countTodayByDistrict(@Param("district") String district);

}
