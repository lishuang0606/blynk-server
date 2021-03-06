package cc.blynk.server.db;

import cc.blynk.server.core.BlockingIOProcessor;
import cc.blynk.server.core.dao.UserKey;
import cc.blynk.server.core.model.AppName;
import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.Profile;
import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.model.enums.PinType;
import cc.blynk.server.core.model.widgets.outputs.graph.GraphGranularityType;
import cc.blynk.server.core.reporting.average.AggregationKey;
import cc.blynk.server.core.reporting.average.AggregationValue;
import cc.blynk.server.core.reporting.average.AverageAggregatorProcessor;
import cc.blynk.server.db.dao.ReportingDBDao;
import cc.blynk.server.db.model.Purchase;
import cc.blynk.server.db.model.Redeem;
import cc.blynk.utils.DateTimeUtils;
import org.junit.*;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.*;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 19.02.16.
 */
public class DBManagerTest {

    private static DBManager dbManager;
    private static BlockingIOProcessor blockingIOProcessor;
    private static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    @BeforeClass
    public static void init() throws Exception {
        blockingIOProcessor = new BlockingIOProcessor(4, 10000);
        dbManager = new DBManager("db-test.properties", blockingIOProcessor, true);
        assertNotNull(dbManager.getConnection());
    }

    @AfterClass
    public static void close() {
        dbManager.close();
    }

    @Before
    public void cleanAll() throws Exception {
        //clean everything just in case
        dbManager.executeSQL("DELETE FROM users");
        dbManager.executeSQL("DELETE FROM reporting_average_minute");
        dbManager.executeSQL("DELETE FROM reporting_average_hourly");
        dbManager.executeSQL("DELETE FROM reporting_average_daily");
        dbManager.executeSQL("DELETE FROM purchase");
        dbManager.executeSQL("DELETE FROM redeem");
    }

    @Test
    public void test() throws Exception {
        assertNotNull(dbManager.getConnection());
    }

    @Test
    @Ignore("Ignoring because of travis CI")
    public void testDbVersion() throws Exception {
        int dbVersion = dbManager.userDBDao.getDBVersion();
        assertTrue(dbVersion >= 90500);
    }

    @Test
    public void testInsert1000RecordsAndSelect() throws Exception {
        int a = 0;

        String userName = "test@gmail.com";

        long start = System.currentTimeMillis();
        long minute = (start / AverageAggregatorProcessor.MINUTE) * AverageAggregatorProcessor.MINUTE;
        long startMinute = minute;

        try (Connection connection = dbManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(ReportingDBDao.insertMinute)) {

            for (int i = 0; i < 1000; i++) {
                ReportingDBDao.prepareReportingInsert(ps, userName, 1, 2, (byte) 0, 'v', minute, (double) i);
                ps.addBatch();
                minute += AverageAggregatorProcessor.MINUTE;
                a++;
            }

            ps.executeBatch();
            connection.commit();
        }

        System.out.println("Finished : " + (System.currentTimeMillis() - start)  + " millis. Executed : " + a);


        try (Connection connection = dbManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select * from reporting_average_minute order by ts ASC")) {

            int i = 0;
            while (rs.next()) {
                assertEquals(userName, rs.getString("email"));
                assertEquals(1, rs.getInt("project_id"));
                assertEquals(2, rs.getInt("device_id"));
                assertEquals(0, rs.getByte("pin"));
                assertEquals("v", rs.getString("pinType"));
                assertEquals(startMinute, rs.getTimestamp("ts", UTC).getTime());
                assertEquals((double) i, rs.getDouble("value"), 0.0001);
                startMinute += AverageAggregatorProcessor.MINUTE;
                i++;
            }
            connection.commit();
        }
    }

    @Test
    @Ignore
    public void testCopy100RecordsIntoFile() throws Exception {
        System.out.println("Starting");

        int a = 0;

        long start = System.currentTimeMillis();
        try (Connection connection = dbManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(ReportingDBDao.insertMinute)) {

            String userName = "test@gmail.com";
            long minute = (System.currentTimeMillis() / AverageAggregatorProcessor.MINUTE) * AverageAggregatorProcessor.MINUTE;

            for (int i = 0; i < 100; i++) {
                ReportingDBDao.prepareReportingInsert(ps, userName, 1, 0, (byte) 0, 'v', minute, (double) i);
                ps.addBatch();
                minute += AverageAggregatorProcessor.MINUTE;
                a++;
            }

            ps.executeBatch();
            connection.commit();
        }

        System.out.println("Finished : " + (System.currentTimeMillis() - start)  + " millis. Executed : " + a);


        try (Connection connection = dbManager.getConnection();
             Writer gzipWriter = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(new File("/home/doom369/output.csv.gz"))), "UTF-8")) {

            CopyManager copyManager = new CopyManager(connection.unwrap(BaseConnection.class));


            String selectQuery = "select pintype || pin, ts, value from reporting_average_minute where project_id = 1 and email = 'test@gmail.com'";
            long res = copyManager.copyOut("COPY (" + selectQuery + " ) TO STDOUT WITH (FORMAT CSV)", gzipWriter);
            System.out.println(res);
        }


    }

    @Test
    public void testDeleteWorksAsExpected() throws Exception {
        long minute;
        try (Connection connection = dbManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(ReportingDBDao.insertMinute)) {

            minute = (System.currentTimeMillis() / AverageAggregatorProcessor.MINUTE) * AverageAggregatorProcessor.MINUTE;

            for (int i = 0; i < 370; i++) {
                ReportingDBDao.prepareReportingInsert(ps, "test1111@gmail.com", 1, 0, (byte) 0, 'v', minute, (double) i);
                ps.addBatch();
                minute += AverageAggregatorProcessor.MINUTE;
            }

            ps.executeBatch();
            connection.commit();
        }
        //todo finish.
        //todo this breaks testInsert1000RecordsAndSelect() test
        //Instant now = Instant.ofEpochMilli(minute);
        //dbManager.cleanOldReportingRecords(now);

    }


    @Test
    public void testManyConnections() throws Exception {
        User user = new User();
        user.email = "test@test.com";
        user.appName = AppName.BLYNK;
        Map<AggregationKey, AggregationValue> map = new ConcurrentHashMap<>();
        AggregationValue value = new AggregationValue();
        value.update(1);
        long ts = System.currentTimeMillis();
        for (int i = 0; i < 60; i++) {
            map.put(new AggregationKey(user.email, user.appName, i, 0, PinType.ANALOG, (byte) i, ts), value);
            dbManager.insertReporting(map, GraphGranularityType.MINUTE);
            dbManager.insertReporting(map, GraphGranularityType.HOURLY);
            dbManager.insertReporting(map, GraphGranularityType.DAILY);

            map.clear();
        }

        while (blockingIOProcessor.getActiveCount() > 0) {
            Thread.sleep(100);
        }

    }

    @Test
    @Ignore("Ignored cause travis postgres is old and doesn't support upserts")
    public void testUpsertForDifferentApps() throws Exception {
        ArrayList<User> users = new ArrayList<>();
        users.add(new User("test1@gmail.com", "pass", "testapp2", "local", false, false));
        users.add(new User("test1@gmail.com", "pass", "testapp1", "local", false, false));
        dbManager.userDBDao.save(users);
        ConcurrentMap<UserKey, User> dbUsers = dbManager.userDBDao.getAllUsers("local");
        assertEquals(2, dbUsers.size());
    }

    @Test
    @Ignore("Ignored cause travis postgres is old and doesn't support upserts")
    public void testUpsertAndSelect() throws Exception {
        ArrayList<User> users = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            users.add(new User("test" + i + "@gmail.com", "pass", AppName.BLYNK, "local", false, false));
        }
        //dbManager.saveUsers(users);
        dbManager.userDBDao.save(users);

        ConcurrentMap<UserKey, User> dbUsers = dbManager.userDBDao.getAllUsers("local");
        System.out.println("Records : " + dbUsers.size());
    }

    @Test
    @Ignore("Ignored cause travis postgres is old and doesn't support upserts")
    public void testUpsertUser() throws Exception {
        ArrayList<User> users = new ArrayList<>();
        User user = new User("test@gmail.com", "pass", AppName.BLYNK, "local", false, false);
        user.name = "123";
        user.lastModifiedTs = 0;
        user.lastLoggedAt = 1;
        user.lastLoggedIP = "127.0.0.1";
        users.add(user);
        user = new User("test@gmail.com", "pass", AppName.BLYNK, "local", false, false);
        user.lastModifiedTs = 0;
        user.lastLoggedAt = 1;
        user.lastLoggedIP = "127.0.0.1";
        user.name = "123";
        users.add(user);
        user = new User("test2@gmail.com", "pass", AppName.BLYNK, "local", false, false);
        user.lastModifiedTs = 0;
        user.lastLoggedAt = 1;
        user.lastLoggedIP = "127.0.0.1";
        user.name = "123";
        users.add(user);

        dbManager.userDBDao.save(users);

        try (Connection connection = dbManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select * from users where email = 'test@gmail.com'")) {
            while (rs.next()) {
                assertEquals("test@gmail.com", rs.getString("email"));
                assertEquals(AppName.BLYNK, rs.getString("appName"));
                assertEquals("local", rs.getString("region"));
                assertEquals("123", rs.getString("name"));
                assertEquals("pass", rs.getString("pass"));
                assertEquals(0, rs.getTimestamp("last_modified", DateTimeUtils.UTC_CALENDAR).getTime());
                assertEquals(1, rs.getTimestamp("last_logged", DateTimeUtils.UTC_CALENDAR).getTime());
                assertEquals("127.0.0.1", rs.getString("last_logged_ip"));
                assertFalse(rs.getBoolean("is_facebook_user"));
                assertFalse(rs.getBoolean("is_super_admin"));
                assertEquals(2000, rs.getInt("energy"));

                assertEquals("{}", rs.getString("json"));
            }
            connection.commit();
        }
    }

    @Test
    @Ignore("Ignored cause travis postgres is old and doesn't support upserts")
    public void testUpsertUserFieldUpdated() throws Exception {
        ArrayList<User> users = new ArrayList<>();
        User user = new User("test@gmail.com", "pass", AppName.BLYNK, "local", false, false);
        user.lastModifiedTs = 0;
        user.lastLoggedAt = 1;
        user.lastLoggedIP = "127.0.0.1";
        users.add(user);

        dbManager.userDBDao.save(users);

        users = new ArrayList<>();
        user = new User("test@gmail.com", "pass2", AppName.BLYNK, "local2", true, true);
        user.name = "1234";
        user.lastModifiedTs = 1;
        user.lastLoggedAt = 2;
        user.lastLoggedIP = "127.0.0.2";
        user.energy = 1000;
        user.profile = new Profile();
        DashBoard dash = new DashBoard();
        dash.id = 1;
        dash.name = "123";
        user.profile.dashBoards = new DashBoard[]{dash};

        users.add(user);

        dbManager.userDBDao.save(users);

        try (Connection connection = dbManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select * from users where email = 'test@gmail.com'")) {
            while (rs.next()) {
                assertEquals("test@gmail.com", rs.getString("email"));
                assertEquals(AppName.BLYNK, rs.getString("appName"));
                assertEquals("local2", rs.getString("region"));
                assertEquals("pass2", rs.getString("pass"));
                assertEquals("1234", rs.getString("name"));
                assertEquals(1, rs.getTimestamp("last_modified", DateTimeUtils.UTC_CALENDAR).getTime());
                assertEquals(2, rs.getTimestamp("last_logged", DateTimeUtils.UTC_CALENDAR).getTime());
                assertEquals("127.0.0.2", rs.getString("last_logged_ip"));
                assertTrue(rs.getBoolean("is_facebook_user"));
                assertTrue(rs.getBoolean("is_super_admin"));
                assertEquals(1000, rs.getInt("energy"));

                assertEquals("{\"dashBoards\":[{\"id\":1,\"name\":\"123\",\"createdAt\":0,\"updatedAt\":0,\"theme\":\"Blynk\",\"keepScreenOn\":false,\"isAppConnectedOn\":false,\"isShared\":false,\"isActive\":false}]}", rs.getString("json"));
            }
            connection.commit();
        }
    }

    @Test
    @Ignore("Ignored cause travis postgres is old and doesn't support upserts")
    public void testInsertAndGetUser() throws Exception {
        ArrayList<User> users = new ArrayList<>();
        User user = new User("test@gmail.com", "pass", AppName.BLYNK, "local", true, true);
        user.lastModifiedTs = 0;
        user.lastLoggedAt = 1;
        user.lastLoggedIP = "127.0.0.1";
        user.profile = new Profile();
        DashBoard dash = new DashBoard();
        dash.id = 1;
        dash.name = "123";
        user.profile.dashBoards = new DashBoard[]{dash};
        users.add(user);

        dbManager.userDBDao.save(users);

        ConcurrentMap<UserKey, User> dbUsers = dbManager.userDBDao.getAllUsers("local");

        assertNotNull(dbUsers);
        assertEquals(1, dbUsers.size());
        User dbUser = dbUsers.get(new UserKey(user.email, user.appName));

        assertEquals("test@gmail.com", dbUser.email);
        assertEquals(AppName.BLYNK, dbUser.appName);
        assertEquals("local", dbUser.region);
        assertEquals("pass", dbUser.pass);
        assertEquals(0, dbUser.lastModifiedTs);
        assertEquals(1, dbUser.lastLoggedAt);
        assertEquals("127.0.0.1", dbUser.lastLoggedIP);
        assertEquals("{\"dashBoards\":[{\"id\":1,\"parentId\":-1,\"isPreview\":false,\"name\":\"123\",\"createdAt\":0,\"updatedAt\":0,\"theme\":\"Blynk\",\"keepScreenOn\":false,\"isAppConnectedOn\":false,\"isShared\":false,\"isActive\":false}]}", dbUser.profile.toString());
        assertTrue(dbUser.isFacebookUser);
        assertTrue(dbUser.isSuperAdmin);
        assertEquals(2000, dbUser.energy);

        assertEquals("{\"dashBoards\":[{\"id\":1,\"parentId\":-1,\"isPreview\":false,\"name\":\"123\",\"createdAt\":0,\"updatedAt\":0,\"theme\":\"Blynk\",\"keepScreenOn\":false,\"isAppConnectedOn\":false,\"isShared\":false,\"isActive\":false}]}", dbUser.profile.toString());
    }

    @Test
    @Ignore("Ignored cause travis postgres is old and doesn't support upserts")
    public void testInsertGetDeleteUser() throws Exception {
        ArrayList<User> users = new ArrayList<>();
        User user = new User("test@gmail.com", "pass", AppName.BLYNK, "local", true, true);
        user.lastModifiedTs = 0;
        user.lastLoggedAt = 1;
        user.lastLoggedIP = "127.0.0.1";
        user.profile = new Profile();
        DashBoard dash = new DashBoard();
        dash.id = 1;
        dash.name = "123";
        user.profile.dashBoards = new DashBoard[]{dash};
        users.add(user);

        dbManager.userDBDao.save(users);

        Map<UserKey, User> dbUsers = dbManager.userDBDao.getAllUsers("local");

        assertNotNull(dbUsers);
        assertEquals(1, dbUsers.size());
        User dbUser = dbUsers.get(new UserKey(user.email, user.appName));

        assertEquals("test@gmail.com", dbUser.email);
        assertEquals(AppName.BLYNK, dbUser.appName);
        assertEquals("local", dbUser.region);
        assertEquals("pass", dbUser.pass);
        assertEquals(0, dbUser.lastModifiedTs);
        assertEquals(1, dbUser.lastLoggedAt);
        assertEquals("127.0.0.1", dbUser.lastLoggedIP);
        assertEquals("{\"dashBoards\":[{\"id\":1,\"parentId\":-1,\"isPreview\":false,\"name\":\"123\",\"createdAt\":0,\"updatedAt\":0,\"theme\":\"Blynk\",\"keepScreenOn\":false,\"isAppConnectedOn\":false,\"isShared\":false,\"isActive\":false}]}", dbUser.profile.toString());
        assertTrue(dbUser.isFacebookUser);
        assertTrue(dbUser.isSuperAdmin);
        assertEquals(2000, dbUser.energy);

        assertEquals("{\"dashBoards\":[{\"id\":1,\"parentId\":-1,\"isPreview\":false,\"name\":\"123\",\"createdAt\":0,\"updatedAt\":0,\"theme\":\"Blynk\",\"keepScreenOn\":false,\"isAppConnectedOn\":false,\"isShared\":false,\"isActive\":false}]}", dbUser.profile.toString());

        assertTrue(dbManager.userDBDao.deleteUser(new UserKey(user.email, user.appName)));
        dbUsers = dbManager.userDBDao.getAllUsers("local");
        assertNotNull(dbUsers);
        assertEquals(0, dbUsers.size());
    }

    @Test
    public void testRedeem() throws Exception {
        assertNull(dbManager.selectRedeemByToken("123"));
        String token = UUID.randomUUID().toString().replace("-", "");
        dbManager.executeSQL("insert into redeem (token) values('" + token + "')");
        assertNotNull(dbManager.selectRedeemByToken(token));
        assertNull(dbManager.selectRedeemByToken("123"));
    }

    @Test
    public void testPurchase() throws Exception {
        dbManager.insertPurchase(new Purchase("test@gmail.com", 1000, "123456"));


        try (Connection connection = dbManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select * from purchase")) {

            while (rs.next()) {
                assertEquals("test@gmail.com", rs.getString("email"));
                assertEquals(1000, rs.getInt("reward"));
                assertEquals("123456", rs.getString("transactionId"));
                assertEquals(0.99D, rs.getDouble("price"), 0.1D);
                assertNotNull(rs.getDate("ts"));
            }

            connection.commit();
        }
    }

    @Test
    public void testOptimisticLockingRedeem() throws Exception {
        String token = UUID.randomUUID().toString().replace("-", "");
        dbManager.executeSQL("insert into redeem (token) values('" + token + "')");

        Redeem redeem = dbManager.selectRedeemByToken(token);
        assertNotNull(redeem);
        assertEquals(redeem.token, token);
        assertFalse(redeem.isRedeemed);
        assertEquals(1, redeem.version);
        assertNull(redeem.ts);

        assertTrue(dbManager.updateRedeem("user@user.com", token));
        assertFalse(dbManager.updateRedeem("user@user.com", token));

        redeem = dbManager.selectRedeemByToken(token);
        assertNotNull(redeem);
        assertEquals(redeem.token, token);
        assertTrue(redeem.isRedeemed);
        assertEquals(2, redeem.version);
        assertEquals("user@user.com", redeem.email);
        assertNotNull(redeem.ts);
    }

    @Test
    public void testSelect() throws Exception {
        long ts = 1455924480000L;
        try (Connection connection = dbManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(ReportingDBDao.selectMinute)) {

            ReportingDBDao.prepareReportingSelect(ps, ts, 2);
             ResultSet rs = ps.executeQuery();


            while(rs.next()) {
                System.out.println(rs.getLong("ts") + " " + rs.getDouble("value"));
            }

            rs.close();
        }
    }

    @Test
    public void cleanOutdatedRecords() throws Exception{
        dbManager.reportingDBDao.cleanOldReportingRecords(Instant.now());
    }

}
