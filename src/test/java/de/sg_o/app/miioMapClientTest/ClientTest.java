package de.sg_o.app.miioMapClientTest;

import de.sg_o.app.miio.base.Token;
import de.sg_o.app.miioMapClient.Client;
import de.sg_o.app.miioMapServer.Server;
import de.sg_o.app.miioMapServer.VacuumMap;
import de.sg_o.proto.MapErrorProto;
import de.sg_o.proto.MapInfoProto;
import org.junit.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;

import static org.junit.Assert.*;

public class ClientTest {
    private static Server s0;
    private Client c0;
    private Token tk;

    @BeforeClass
    public static void serverSetUp() throws Exception{
        ClassLoader classLoader = ClientTest.class.getClassLoader();
        File currentMap = new File(Objects.requireNonNull(classLoader.getResource("run/shm/")).getFile());
        File oldMap = new File(Objects.requireNonNull(classLoader.getResource("mnt/data/rockrobo/rrlog")).getFile());

        File token = new File(Objects.requireNonNull(classLoader.getResource("mnt/data/miio/device.token")).getFile());
        s0 = new Server(currentMap, oldMap, 2000, token, Level.ALL, null);

        new Thread(s0).start();
    }

    @Before
    public void setUp() throws Exception {
        tk = new Token("61626364656667683132333441424344", 16);

        c0 = new Client("127.0.0.1", 2000, tk, 5000);
    }

    @After
    public void tearDown() {
        c0.close();
    }

    @AfterClass
    public static void serverTearDown() {
        s0.terminate();
    }

    @Test
    public void authenticateTest() throws Exception {
        try {
            c0.getActive();
            fail();
        } catch (IOException e) {
            assertEquals("NOT_AUTHENTICATED", e.getMessage());
        }
        c0 = new Client("127.0.0.1", 2000, tk, 5000);

        assertTrue(c0.authenticate());

        assertTrue(c0.getMapInfo().getActiveMapAvailable());

        c0.close();
        c0 = new Client("127.0.0.1", 2000, new Token(null, 16), 5000);
        assertFalse(c0.authenticate());
    }

    @Test
    public void getMapInfoTest() throws Exception {
        try {
            c0.getMapInfo();
            fail();
        } catch (IOException e) {
            assertEquals("NOT_AUTHENTICATED", e.getMessage());
        }
        c0.close();
        c0 = new Client("127.0.0.1", 2000, tk, 5000);
        assertTrue(c0.authenticate());
        MapInfoProto.MapInfo info = c0.getMapInfo();
        assertEquals(2, info.getOldMapsCount());
        assertTrue(info.getActiveMapAvailable());
        assertEquals(MapErrorProto.MapError.ErrorCode.NONE, info.getError().getCode());
    }

    @Test
    public void getActiveMapTest() throws Exception {
        try {
            c0.getActiveMap();
            fail();
        } catch (IOException e) {
            assertEquals("NOT_AUTHENTICATED", e.getMessage());
        }
        c0.close();
        c0 = new Client("127.0.0.1", 2000, tk, 5000);
        try {
            c0.getActiveMapSlam(0);
            fail();
        } catch (IOException e) {
            assertEquals("NOT_AUTHENTICATED", e.getMessage());
        }
        c0.close();
        c0 = new Client("127.0.0.1", 2000, tk, 5000);
        ClassLoader classLoader = ClientTest.class.getClassLoader();
        File activeFileMap = new File(Objects.requireNonNull(classLoader.getResource("run/shm/navmap0.ppm")).getFile());
        File activeFileSlam = new File(Objects.requireNonNull(classLoader.getResource("run/shm/SLAM_fprintf.log")).getFile());

        BufferedReader map = new BufferedReader(new FileReader(activeFileMap));
        BufferedReader slam = new BufferedReader(new FileReader(activeFileSlam));

        VacuumMap m0 = new VacuumMap(map, slam, 1, null);

        map.close();
        slam.close();
        map = new BufferedReader(new FileReader(activeFileMap));
        slam = new BufferedReader(new FileReader(activeFileSlam));

        VacuumMap m1 = new VacuumMap(map, slam, 1, null);

        map.close();
        slam.close();

        assertTrue(c0.authenticate());
        assertEquals(m0, c0.getActive());

        c0.updateActivePath(m1);
        assertEquals(m0, m1);
    }

    @Test
    public void getPreviousMapTest() throws Exception {
        assertTrue(c0.authenticate());
        assertEquals("de.sg_o.app.miioMapServer.VacuumMap{map=width:1024; height:1024, pathEntries=4598, boundingBox=[460, 409, 90, 118], overSample=1}", c0.getPrevious().toString());
    }

    @Test
    public void getOldMapTest() throws Exception {
        assertTrue(c0.authenticate());
        VacuumMap old = c0.getOld("000143.20180604001001609_1387101062713_2018032100REL");
        assertEquals("de.sg_o.app.miioMapServer.VacuumMap{map=width:1024; height:1024, pathEntries=4831, boundingBox=[442, 450, 133, 117], overSample=1}", old.toString());
    }
}
