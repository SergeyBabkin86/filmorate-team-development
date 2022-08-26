package ru.yandex.practicum.filmorate.storage.film;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.filmorate.model.Director;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.storage.director.DirectorDaoStorage;
import ru.yandex.practicum.filmorate.storage.genre.GenreDaoStorage;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static ru.yandex.practicum.filmorate.utilities.Checker.checkFilmExists;
import static ru.yandex.practicum.filmorate.utilities.Checker.checkUserExists;
import static ru.yandex.practicum.filmorate.utilities.Validator.filmValidator;

@Component
@Slf4j
@RequiredArgsConstructor
public class FilmDbStorage implements FilmDaoStorage {

    private final JdbcTemplate jdbcTemplate;
    private final DirectorDaoStorage directorDaoStorage;
    private final GenreDaoStorage genreDaoStorage;

    @Override
    public Film getFilmById(Long id) {
        checkFilmExists(id, jdbcTemplate);
        String sql =
                "SELECT F.FILM_ID, F.NAME, F.DESCRIPTION, F.RELEASE_DATE,  " +
                        "F.DURATION, F.RATING_ID, R.RATING_NAME " +
                        "FROM FILMS F " +
                        "JOIN RATINGS AS R ON F.RATING_ID = R.RATING_ID " +
                        "WHERE F.FILM_ID = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> makeFilm(rs), id)
                .stream().findFirst().get();
    }

    @Override
    public List<Film> getAllFilms() {
        String sql =
                "SELECT F.FILM_ID, F.NAME, F.RELEASE_DATE, F.DESCRIPTION,  " +
                        "F.DURATION, F.RATING_ID, R.RATING_NAME " +
                        "FROM FILMS f " +
                        "JOIN RATINGS AS R ON f.RATING_ID = R.RATING_ID " +
                        "ORDER BY F.FILM_ID";
        return jdbcTemplate.query(sql, (rs, rowNum) -> makeFilm(rs));
    }

    @Override
    public Film createFilm(Film film) {
        filmValidator(film);

        String sqlQuery = "INSERT INTO films (NAME, RELEASE_DATE, DESCRIPTION, DURATION, RATING_ID) " +
                "VALUES (?, ?, ?, ?, ?);";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sqlQuery, new String[]{"FILM_ID"});
            ps.setString(1, film.getName());
            ps.setDate(2, Date.valueOf(film.getReleaseDate()));
            ps.setString(3, film.getDescription());
            ps.setLong(4, film.getDuration());
            ps.setLong(5, film.getMpa().getId());
            return ps;
        }, keyHolder);
        film.setId(keyHolder.getKey().longValue());
        return film;
    }

    @Override
    public void createGenreByFilm(Film film) {
        filmValidator(film);
        String sql =
                "INSERT INTO FILMS_GENRES (FILM_ID, GENRE_ID) " +
                        "VALUES(?, ?)";
        Set<Genre> genres = film.getGenres();
        if (genres == null) {
            return;
        }
        for (Genre genre : genres) {
            jdbcTemplate.update(sql, film.getId(), genre.getId());
        }
    }

    @Override
    public void createDirectorByFilm(Film film) {
        filmValidator(film);
        String sql =
                "INSERT INTO FILM_DIRECTOR (FILM_ID, DIRECTOR_ID) " +
                        "VALUES(?, ?)";
        Set<Director> directors = film.getDirectors();
        if (directors == null) {
            return;
        }
        for (Director director : directors) {
            jdbcTemplate.update(sql, film.getId(), director.getId());
        }
    }

    @Override
    public Film updateFilm(Film film) {
        filmValidator(film);
        checkFilmExists(film.getId(), jdbcTemplate);
        String sql =
                "UPDATE FILMS " +
                        "SET NAME = ?, RELEASE_DATE = ?, DESCRIPTION = ?, " +
                        "DURATION = ?, RATING_ID =? " +
                        "WHERE FILM_ID = ?";
        jdbcTemplate.update(sql, film.getName(), film.getReleaseDate(), film.getDescription(),
                film.getDuration(), film.getMpa().getId(), film.getId());
        return film;
    }

    @Override
    public void deleteFilm(Long id) {
        checkFilmExists(id, jdbcTemplate);
        String sql =
                "DELETE " +
                        "FROM FILMS " +
                        "WHERE FILM_ID = ?";
        jdbcTemplate.update(sql, id);
    }

    @Override
    public List<Film> getDirectorsFilmSortByYear(Integer directorId) {
        String sqlQuery =
                "SELECT f.FILM_ID, f.NAME, f.RELEASE_DATE, f.DESCRIPTION, f.DURATION, f.RATING_ID, r.RATING_NAME " +
                        "FROM FILMS AS f " +
                        "JOIN RATINGS r on r.RATING_ID = f.RATING_ID " +
                        "INNER JOIN FILM_DIRECTOR AS fd on f.FILM_ID = fd.FILM_ID " +
                        "WHERE fd.DIRECTOR_ID = ? " +
                        "ORDER BY f.RELEASE_DATE";
        return getSortedFilmsList(directorId, sqlQuery);
    }

    @Override
    public List<Film> getDirectorsFilmSortByLikesCount(Integer directorId) {
        String sqlQuery = "SELECT f.FILM_ID, f.NAME, f.RELEASE_DATE, f.DESCRIPTION, f.DURATION, " +
                " f.RATING_ID, r.RATING_NAME " +
                "FROM FILMS as f " +
                "JOIN RATINGS r on r.RATING_ID = f.RATING_ID " +
                "LEFT JOIN FILMS_LIKES fl on f.film_id = fl.film_id " +
                "LEFT JOIN FILM_DIRECTOR fd on f.FILM_ID = fd.FILM_ID " +
                "WHERE fd.DIRECTOR_ID = ? " +
                "GROUP BY f.FILM_ID " +
                "ORDER BY COUNT(fl.USER_ID) DESC";
        return getSortedFilmsList(directorId, sqlQuery);
    }

    @Override
    public List<Film> getTopLikeFilm(Integer count) {
        String sql =
                "SELECT F.FILM_ID, F.NAME, F.RELEASE_DATE, F.DESCRIPTION,  " +
                        "F.DURATION, F.RATING_ID, R.RATING_NAME " +
                        "FROM FILMS F " +
                        "JOIN RATINGS AS R ON f.RATING_ID = R.RATING_ID " +
                        "LEFT JOIN FILMS_LIKES L on F.FILM_ID = L.FILM_ID " +
                        "GROUP BY F.FILM_ID " +
                        "ORDER BY COUNT(L.USER_ID) DESC " +
                        "limit ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> makeFilm(rs), count);
    }

    @Override
    public List<Film> getTopFilmsGenreYear(Integer count, Integer genreId, Integer year) {
        String sql =
                "SELECT f.FILM_ID, f.NAME, f.RELEASE_DATE, f.DESCRIPTION, f.DURATION, " +
                        " f.RATING_ID, r.RATING_NAME, GEN.GENRE_ID " +
                        "FROM FILMS f " +
                        "JOIN RATINGS AS r ON r.RATING_ID = f.RATING_ID " +
                        "LEFT JOIN FILMS_LIKES l on f.FILM_ID = l.FILM_ID " +
                        "LEFT JOIN FILMS_GENRES fg on f.FILM_ID = fg.FILM_ID " +
                        "JOIN GENRES GEN on FG.GENRE_ID = GEN.GENRE_ID " +
                        "WHERE YEAR(F.RELEASE_DATE) = ? AND GEN.GENRE_ID = ?  " +
                        "GROUP BY F.FILM_ID " +
                        "ORDER BY COUNT(l.USER_ID) DESC " +
                        "LIMIT ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> makeFilm(rs), year, genreId, count);
    }

    @Override
    public List<Film> getTopFilmsGenre(Integer count, Integer genreId) {
        String sql =
                "SELECT f.FILM_ID, f.NAME, f.RELEASE_DATE, f.DESCRIPTION, f.DURATION, " +
                        " f.RATING_ID, r.RATING_NAME " +
                        "FROM FILMS f " +
                        "JOIN RATINGS AS r ON r.RATING_ID = f.RATING_ID " +
                        "LEFT JOIN FILMS_LIKES l on f.FILM_ID = l.FILM_ID " +
                        "LEFT JOIN FILMS_GENRES fg on f.FILM_ID = fg.FILM_ID " +
                        "JOIN GENRES GEN on FG.GENRE_ID = GEN.GENRE_ID " +
                        "WHERE GEN.GENRE_ID = ? " +
                        "GROUP BY F.FILM_ID " +
                        "ORDER BY COUNT(l.USER_ID) DESC " +
                        "LIMIT ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> makeFilm(rs), genreId, count);
    }

    @Override
    public List<Film> getTopFilmsYear(Integer count, Integer year) {
        String sql =
                "SELECT f.FILM_ID, f.NAME, f.RELEASE_DATE, f.DESCRIPTION, f.DURATION, " +
                        " f.RATING_ID, r.RATING_NAME " +
                        "FROM FILMS f " +
                        "JOIN RATINGS AS r ON r.RATING_ID = f.RATING_ID " +
                        "LEFT JOIN FILMS_LIKES l on f.FILM_ID = l.FILM_ID " +
                        "LEFT JOIN FILMS_GENRES fg on f.FILM_ID = fg.FILM_ID " +
                        "JOIN GENRES GEN on FG.GENRE_ID = GEN.GENRE_ID " +
                        "WHERE YEAR(F.RELEASE_DATE) = ? " +
                        "GROUP BY F.FILM_ID " +
                        "ORDER BY COUNT(l.USER_ID) DESC " +
                        "LIMIT ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> makeFilm(rs), year, count);
    }

    public List<Film> getCommonFilms(Long userId, Long friendId) {
        checkUserExists(userId, jdbcTemplate);
        checkUserExists(friendId, jdbcTemplate);
        String sqlQuery = "SELECT distinct F.film_id, name, description, release_date, duration, R.rating_id, " +
                "RATING_NAME FROM FILMS F " +
                "LEFT JOIN FILMS_LIKES FL on F.FILM_ID = FL.FILM_ID " +
                "LEFT JOIN RATINGS R on F.RATING_ID = R.RATING_ID " +
                "WHERE USER_ID IN (?, ?) " +
                "HAVING COUNT(USER_ID) > 1;";
        return jdbcTemplate.query(sqlQuery, (rs, rowNum) -> makeFilm(rs), userId, friendId);
    }

    @Override
    public List<Film> getSearchFilmsForTitle(String query) {
        String sql =
                "SELECT f.film_id, f.NAME, f.DESCRIPTION, f.RELEASE_DATE, f.DURATION, " +
                        "f.RATING_ID, R.RATING_NAME " +
                        "FROM films f " +
                        "LEFT JOIN RATINGS R ON f.RATING_ID = R.RATING_ID " +
                        "LEFT JOIN FILMS_LIKES l on l.FILM_ID = f.FILM_ID " +
                        "WHERE (f.NAME) ILIKE '%' || (?) || '%' " +
                        "GROUP BY f.FILM_ID " +
                        "ORDER BY count(l.USER_ID) DESC";

        List<Film> list = jdbcTemplate.query(sql, (rs, rowNum) -> makeFilm(rs), query);
        log.info("Создан список:" + list);
        return list;
    }

    @Override
    public List<Film> getSearchFilmsForDirector(String query) {
        String sql =
                "SELECT f.FILM_ID, f.NAME, f.DESCRIPTION, f.RATING_ID as RATING_ID, " +
                        "mr.RATING_NAME, f.DURATION, f.RELEASE_DATE " +
                        "FROM films as f " +
                        "JOIN RATINGS MR on MR.RATING_ID = f.RATING_ID " +
                        "JOIN FILM_DIRECTOR FD on f.FILM_ID = FD.FILM_ID " +
                        "JOIN DIRECTORS D on D.DIRECTOR_ID = FD.DIRECTOR_ID " +
                        "LEFT JOIN FILMS_LIKES L on f.FILM_ID = L.FILM_ID " +
                        "WHERE d.DIRECTOR_NAME ilike '%' || (?) || '%' " +
                        "GROUP BY f.film_id " +
                        "ORDER BY COUNT(l.USER_ID) DESC";

        List<Film> list = jdbcTemplate.query(sql, (rs, rowNum) -> makeFilm(rs), query);
        log.info("Создан список:" + list);
        return list;
    }

    @Override
    public List<Film> getSearchFilmsForTitleAndDirector(String query) {
        String sql = "SELECT f.FILM_ID, f.NAME, f.DESCRIPTION, f.RATING_ID as RATING_ID, " +
                "R.RATING_NAME, f.DURATION, f.RELEASE_DATE " +
                "FROM films as f " +
                "JOIN RATINGS R on R.RATING_ID = f.RATING_ID " +
                "LEFT JOIN FILM_DIRECTOR FD on f.FILM_ID = FD.FILM_ID " +
                "LEFT JOIN DIRECTORS D on D.DIRECTOR_ID = FD.DIRECTOR_ID " +
                "LEFT JOIN FILMS_LIKES FL on f.FILM_ID = FL.FILM_ID " +
                "WHERE f.NAME ilike '%' || (?) || '%' OR d.DIRECTOR_NAME ilike '%' || (?) || '%' " +
                "GROUP BY f.film_id " +
                "ORDER BY COUNT(FL.USER_ID) DESC";

        List<Film> list = jdbcTemplate.query(sql, (rs, rowNum) -> makeFilm(rs), query, query);
        log.info("Создан список:" + list);
        return list;
    }

    @Override
    public List<Film> findFilmsLikedByUser(Long id) {
        String queryToFindUserFilms = "SELECT * FROM FILMS " +
                "JOIN RATINGS R on R.RATING_ID = FILMS.RATING_ID " +
                "WHERE FILMS.FILM_ID iN (SELECT FILM_ID FROM FILMS_LIKES WHERE USER_ID = ?)";
        return jdbcTemplate.query(queryToFindUserFilms, (rs, rowNum) -> makeFilm(rs), id);
    }

    private Film makeFilm(ResultSet rs) throws SQLException {
        Film film = new Film();
        film.setId(rs.getLong("FILM_ID"));
        film.setName(rs.getString("NAME"));
        film.setDescription(rs.getString("DESCRIPTION"));
        film.setReleaseDate(rs.getDate("RELEASE_DATE").toLocalDate());
        film.setDuration(rs.getInt("DURATION"));
        film.setMpa(new Mpa(rs.getInt("RATING_ID"), rs.getString("RATING_NAME")));
        return film;
    }

    private List<Film> getSortedFilmsList(Integer directorId, String sqlQuery) {
        List<Film> films = jdbcTemplate.query(sqlQuery, (rs, rowNum) -> makeFilm(rs), directorId);
        films.forEach(f -> f.setDirectors(directorDaoStorage.getDirectorsByFilm(f)));
        films.forEach(f -> f.setGenres(genreDaoStorage.getGenresByFilm(f)));
        return films;
    }
}