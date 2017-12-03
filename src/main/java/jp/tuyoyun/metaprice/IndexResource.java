package jp.tuyoyun.metaprice;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.stereotype.Component;

@Component
@Path("/")
public class IndexResource {
	private static DateTimeFormatter formatter;
	static {
		formatter = DateTimeFormatter.ofPattern("uuuu.MM.dd HH:mm:ss").withLocale(Locale.JAPAN)
				.withResolverStyle(ResolverStyle.STRICT);

	}
	@Autowired
	JdbcTemplate jdbcTemplate;

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public String index(List<Price> prices) {
		try {
			Price oldest = prices.get(prices.size() - 1);
			Date date = toDate(formatter, oldest.getTime());

			List<Map<String, Object>> queryForList = jdbcTemplate.queryForList(
					"select TIME from PRICE2 WHERE TIME>=? AND CURRENCY=?",
					new Object[] { new java.sql.Date(date.getTime()), prices.iterator().next().getCurrency() });

			Set<Date> set = queryForList.stream().map(map -> new Date(((Date) map.get("TIME")).getTime()))
					.collect(Collectors.toSet());

			insert(prices, set);
		} catch (Exception e) {
			e.printStackTrace();
			return "error";
		}
		return "ok";
	}

	@Transactional
	private void insert(List<Price> prices, Set<Date> set) {
		prices.stream().forEachOrdered(price -> {
			Date date = toDate(formatter, price.getTime());
			if (set.contains(date)) {
				return;
			}
			jdbcTemplate.update(
					"Insert into PRICE2 (PRICE_ID,PERIOD,CURRENCY,TIME,OPEN,CLOSE,HIGH,LOW,DAY,UP,TIME_M15,TIME_H4)"
							+ " values (PRICE2_SEQ.NEXTVAL,1,?,to_date(?,'YYYY.MM.DD HH24:MI:SS'),?,?,?,?"
							+ ",to_date(?,'YYYY.MM.DD HH24:MI:SS'),?,to_date(?,'YYYY.MM.DD HH24:MI:SS'),to_date(?,'YYYY.MM.DD HH24:MI:SS'))",
					new PreparedStatementSetter() {
						@Override
						public void setValues(PreparedStatement ps) throws SQLException {
							int i = 1;
							LocalDateTime localTime = getLocalTime(formatter, price.getTime());
							String timeStr = formatter.format(localTime);
							Date truncate = DateUtils.truncate(date, Calendar.DAY_OF_MONTH);
							LocalDateTime truncLocalDate = toLocalDateTime(truncate);
							String truncateStr = formatter.format(truncLocalDate);

							// M15
							int minute = localTime.getMinute();
							minute = minute / 15 * 15;
							LocalDateTime localTimeM15 = LocalDateTime.of(localTime.getYear(), localTime.getMonth(),
									localTime.getDayOfMonth(), localTime.getHour(), minute, 0);
							String timeStrM15 = formatter.format(localTimeM15);
							// H4
							int hour = localTime.getHour();
							hour = hour / 4 * 4;
							LocalDateTime localTimeH4 = LocalDateTime.of(localTime.getYear(), localTime.getMonth(),
									localTime.getDayOfMonth(), hour, 0, 0);
							String timeStrH4 = formatter.format(localTimeH4);

							ps.setString(i++, price.getCurrency());
							ps.setString(i++, timeStr);
							ps.setString(i++, price.getOpen().toString());
							ps.setString(i++, price.getClose().toString());
							ps.setString(i++, price.getHigh().toString());
							ps.setString(i++, price.getLow().toString());
							ps.setString(i++, truncateStr);
							ps.setInt(i++, price.getClose().doubleValue() > price.getOpen().doubleValue() ? 1 : 0);
							ps.setString(i++, timeStrM15);
							ps.setString(i++, timeStrH4);
						}
					});
		});
	}

	public static Date toDate(DateTimeFormatter formatter, String str) {
		LocalDateTime localDateTime = getLocalTime(formatter, str);
		return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
	}

	private static LocalDateTime getLocalTime(DateTimeFormatter formatter, String str) {
		LocalDateTime localDateTime = LocalDateTime.parse(str, formatter);
		return localDateTime;
	}

	public static LocalDate toLocalDate(Date date) {
		return toLocalDateTime(date).toLocalDate();
	}

	public static LocalTime toLocalTime(Date date) {
		return toLocalDateTime(date).toLocalTime();
	}

	public static LocalDateTime toLocalDateTime(Date date) {
		return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
	}
}