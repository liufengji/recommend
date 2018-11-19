package com.victor.bussiness.service;

import com.victor.bussiness.model.domain.Rating;
import com.victor.bussiness.model.domain.User;
import com.victor.bussiness.model.request.MovieRatingRequest;
import com.victor.commons.constant.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Service
public class RatingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private Jedis jedis;

    public boolean movieRating(MovieRatingRequest request) {
        Rating rating = new Rating(request.getUid(), request.getMid(), request.getScore());
        updateRedis(rating);
        if (ratingExist(rating.getUid(), rating.getMid())) {
            return updateRating(rating);
        } else {
            return newRating(rating);
        }
    }

    private void updateRedis(Rating rating) {
        if (jedis.exists("uid:" + rating.getUid()) && jedis.llen("uid:" + rating.getUid()) >= 20) {
            jedis.rpop("uid:" + rating.getUid());
        }
        jedis.lpush("uid:" + rating.getUid(), rating.getMid() + ":" + rating.getScore());
    }

    public boolean newRating(Rating rating) {
        jdbcTemplate.update("insert into " + Constants.DB_RATINGS() + "(uid,mid,score,timestamp) values(?,?,?,?)",
                new Object[]{rating.getUid(), rating.getMid(), rating.getScore(), rating.getTimestamp() / 1000});
        return true;
    }

    public boolean ratingExist(int uid, int mid) {
        return null != findRating(uid, mid);
    }

    public boolean updateRating(Rating rating) {
        jdbcTemplate.update("update " + Constants.DB_RATINGS() + " set score=" + rating.getScore() + " where uid=" + rating.getUid() + " and mid=" + rating.getMid());
        return true;
    }


    public Rating findRating(int uid, int mid) {
        Rating rating = null;
        rating = jdbcTemplate.query("select * from " + Constants.DB_RATINGS() + " where uid=" + uid + " and mid=" + mid, new ResultSetExtractor<Rating>() {
            @Override
            public Rating extractData(ResultSet resultSet) throws SQLException, DataAccessException {
                Rating rating = null;
                if (resultSet.next()) {
                    rating = new Rating();
                    rating.setUid(resultSet.getInt("uid"));
                    rating.setMid(resultSet.getInt("mid"));
                    rating.setScore(resultSet.getDouble("score"));
                    rating.setTimestamp(resultSet.getInt("timestamp"));
                }
                return rating;
            }
        });
        return rating;
    }

    public void removeRating(int uid, int mid) {
        jdbcTemplate.execute("delete from " + Constants.DB_RATINGS() + " where uid=" + uid + " and mid=" + mid);
    }

    public int[] getMyRatingStat(User user) {
        List<Rating> ratings = null;
        ratings = jdbcTemplate.query("select * from " + Constants.DB_RATINGS() + " where uid=" + user.getUid(), new RowMapper<Rating>() {
            @Override
            public Rating mapRow(ResultSet resultSet, int i) throws SQLException {
                Rating rating = new Rating();
                rating.setUid(resultSet.getInt("uid"));
                rating.setMid(resultSet.getInt("mid"));
                rating.setScore(resultSet.getDouble("score"));
                rating.setTimestamp(resultSet.getInt("timestamp"));
                return rating;
            }
        });

        int[] stats = new int[10];
        for (Rating rating : ratings) {
            Long index = Math.round(rating.getScore() / 0.5);
            stats[index.intValue()] = stats[index.intValue()] + 1;
        }
        return stats;
    }

}
