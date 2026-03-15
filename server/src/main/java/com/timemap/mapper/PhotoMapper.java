package com.timemap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.timemap.model.vo.CommunityPhotoVO;
import com.timemap.model.vo.DistrictRankVO;
import com.timemap.model.vo.MyPhotoVO;
import com.timemap.model.vo.NearbyPhotoVO;
import com.timemap.model.vo.UserAreaStatVO;
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
               (SELECT COUNT(*) FROM t_comment WHERE photo_id = t_photo.id AND deleted = 0) AS comment_count,
               (SELECT COUNT(*) FROM t_photo_like WHERE photo_id = t_photo.id) AS like_count,
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
    List<NearbyPhotoVO> findNearby(@Param("lat") double lat,
                                          @Param("lng") double lng,
                                          @Param("radiusKm") double radiusKm,
                                          @Param("startDate") String startDate,
                                          @Param("endDate") String endDate);

    @Select("""
        <script>
        SELECT p.id, p.user_id, p.image_url, p.thumbnail_url, p.longitude, p.latitude,
               p.location_name, p.photo_date, p.create_time, u.nickname, u.avatar_url,
               (SELECT COUNT(*) FROM t_comment c WHERE c.photo_id = p.id AND c.deleted = 0) AS comment_count,
               (SELECT COUNT(*) FROM t_photo_like pl WHERE pl.photo_id = p.id) AS like_count
        FROM t_photo p
        LEFT JOIN t_user u ON p.user_id = u.id
        WHERE p.deleted = 0
          AND p.district = #{district}
        <if test="sortBy == 'createTime'">
        ORDER BY p.create_time DESC, p.id DESC
        </if>
        <if test="sortBy == 'commentCount'">
        ORDER BY comment_count DESC, p.id DESC
        </if>
        <if test="sortBy == 'likeCount'">
        ORDER BY like_count DESC, p.id DESC
        </if>
        <if test="sortBy != 'createTime' and sortBy != 'commentCount' and sortBy != 'likeCount'">
        ORDER BY p.photo_date DESC, p.id DESC
        </if>
        LIMIT #{offset}, #{size}
        </script>
    """)
    List<CommunityPhotoVO> findCommunity(@Param("offset") int offset,
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

    @Select("SELECT COUNT(DISTINCT user_id) FROM t_photo WHERE deleted = 0 AND district = #{district} AND DATE(create_time) = CURDATE()")
    long countTodayUsersByDistrict(@Param("district") String district);

    @Select("""
        <script>
        SELECT COUNT(*) FROM t_photo
        WHERE deleted = 0 AND district = #{district}
          <if test="startDate != null and startDate != ''">
            AND photo_date &gt;= #{startDate}
          </if>
          <if test="endDate != null and endDate != ''">
            AND photo_date &lt;= #{endDate}
          </if>
        </script>
    """)
    long countByDistrictAndDate(@Param("district") String district,
                                @Param("startDate") String startDate,
                                @Param("endDate") String endDate);

    @Select("""
        <script>
        SELECT COUNT(DISTINCT user_id) FROM t_photo
        WHERE deleted = 0 AND district = #{district}
          <if test="startDate != null and startDate != ''">
            AND photo_date &gt;= #{startDate}
          </if>
          <if test="endDate != null and endDate != ''">
            AND photo_date &lt;= #{endDate}
          </if>
        </script>
    """)
    long countUsersByDistrictAndDate(@Param("district") String district,
                                     @Param("startDate") String startDate,
                                     @Param("endDate") String endDate);

    @Select("""
        SELECT p.id, p.image_url, p.thumbnail_url, p.location_name, p.photo_date, p.create_time,
               (SELECT COUNT(*) FROM t_comment c WHERE c.photo_id = p.id AND c.deleted = 0) AS comment_count,
               (SELECT COUNT(*) FROM t_photo_like pl WHERE pl.photo_id = p.id) AS like_count
        FROM t_photo p
        WHERE p.deleted = 0 AND p.user_id = #{userId}
        ORDER BY p.create_time DESC
        LIMIT #{offset}, #{size}
    """)
    List<MyPhotoVO> findMyPhotos(@Param("userId") Long userId,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    @Select("SELECT COUNT(*) FROM t_photo WHERE deleted = 0 AND user_id = #{userId}")
    long countMyPhotos(@Param("userId") Long userId);

    @Select("""
        SELECT COUNT(DISTINCT district)
        FROM t_photo
        WHERE deleted = 0 AND user_id = #{userId}
          AND district IS NOT NULL AND district != ''
    """)
    long countUserAreas(@Param("userId") Long userId);

    @Select("""
        SELECT IFNULL(SUM(sub.cnt), 0) FROM (
            SELECT (SELECT COUNT(*) FROM t_photo_like WHERE photo_id = p.id) AS cnt
            FROM t_photo p WHERE p.deleted = 0 AND p.user_id = #{userId}
        ) sub
    """)
    long countUserTotalLikes(@Param("userId") Long userId);

    @Select("""
        SELECT
          CASE
            WHEN district IS NULL OR district = '' THEN '未标注区域'
            ELSE district
          END AS name,
          COUNT(*) AS count
        FROM t_photo
        WHERE deleted = 0 AND user_id = #{userId}
        GROUP BY CASE
          WHEN district IS NULL OR district = '' THEN '未标注区域'
          ELSE district
        END
        ORDER BY count DESC, name ASC
        LIMIT #{limit}
    """)
    List<UserAreaStatVO> findTopAreas(@Param("userId") Long userId,
                                            @Param("limit") int limit);

    @Select("""
        SELECT DATE_FORMAT(MAX(COALESCE(photo_date, DATE(create_time))), '%Y-%m-%d')
        FROM t_photo
        WHERE deleted = 0 AND user_id = #{userId}
    """)
    String findLatestPhotoDate(@Param("userId") Long userId);

    @Select("""
        <script>
        SELECT
            p.district,
            COUNT(*) AS photo_count,
            COUNT(DISTINCT p.user_id) AS user_count,
            SUM(CASE WHEN DATE(p.create_time) = CURDATE() THEN 1 ELSE 0 END) AS today_count,
            (SELECT COALESCE(p2.thumbnail_url, p2.image_url)
             FROM t_photo p2
             WHERE p2.deleted = 0 AND p2.district = p.district
             ORDER BY p2.create_time DESC LIMIT 1) AS latest_thumb_url
        FROM t_photo p
        WHERE p.deleted = 0
          AND p.district IS NOT NULL AND p.district != ''
        GROUP BY p.district
        <if test="sortBy == 'userCount'">
        ORDER BY user_count DESC, photo_count DESC
        </if>
        <if test="sortBy == 'todayCount'">
        ORDER BY today_count DESC, photo_count DESC
        </if>
        <if test="sortBy != 'userCount' and sortBy != 'todayCount'">
        ORDER BY photo_count DESC, user_count DESC
        </if>
        LIMIT #{limit}
        </script>
    """)
    List<DistrictRankVO> findDistrictRanking(@Param("sortBy") String sortBy,
                                             @Param("limit") int limit);

    @Select("SELECT COUNT(DISTINCT district) FROM t_photo WHERE deleted = 0 AND district IS NOT NULL AND district != ''")
    long countDistinctDistricts();

    @Select("SELECT COUNT(*) FROM t_photo WHERE deleted = 0 AND district IS NOT NULL AND district != ''")
    long countAllPhotosWithDistrict();

}
