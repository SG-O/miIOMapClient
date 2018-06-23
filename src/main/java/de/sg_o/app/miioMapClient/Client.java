package de.sg_o.app.miioMapClient;

import de.sg_o.app.miio.base.Token;
import de.sg_o.app.miio.util.ByteArray;
import de.sg_o.app.miioMapServer.VacuumMap;
import de.sg_o.proto.*;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;

@SuppressWarnings("WeakerAccess")
public class Client {
    private final Socket socket;
    private final Token tk;

    public Client(String host, int port, Token tk, int timeout) throws IOException {
        this.socket = new Socket(host, port);
        if (timeout < 0) timeout = 1000;
        this.socket.setSoTimeout(timeout);
        if (tk == null) throw new IOException("No token provided");
        this.tk = tk;
    }

    public void close(){
        try {
            MapRequestProto.MapRequest.Builder request = MapRequestProto.MapRequest.newBuilder();
            request.setCode(MapRequestProto.MapRequest.RequestCode.END_COMMUNICATION);
            OutputStream outputStream = socket.getOutputStream();
            request.build().writeDelimitedTo(outputStream);
            socket.close();
        } catch (IOException ignore) {
        }
    }

    public synchronized boolean authenticate() throws IOException {
        MapRequestProto.MapRequest.Builder request = MapRequestProto.MapRequest.newBuilder();
        request.setCode(MapRequestProto.MapRequest.RequestCode.AUTHENTICATE);
        request.setOpt(ByteArray.bytesToHex(tk.encrypt("hello".getBytes("ASCII"))));
        OutputStream outputStream = socket.getOutputStream();
        InputStream inputStream = socket.getInputStream();
        request.build().writeDelimitedTo(outputStream);

        MapRequestProto.MapRequest info;
        long endTime = System.currentTimeMillis() + socket.getSoTimeout();
        while ((info = MapRequestProto.MapRequest.parseDelimitedFrom(inputStream)) == null) {
            if (socket.getSoTimeout() == 0) continue;
            if (System.currentTimeMillis() > endTime) throw new IOException("Timeout");
        }

        if (!info.getCode().equals(MapRequestProto.MapRequest.RequestCode.AUTHENTICATE)) return false;
        byte[] response = tk.decrypt(ByteArray.hexToBytes(info.getOpt()));
        return Arrays.equals(new byte[]{111, 107}, response);
    }

    public synchronized MapInfoProto.MapInfo getMapInfo() throws IOException {
        MapRequestProto.MapRequest.Builder request = MapRequestProto.MapRequest.newBuilder();
        request.setCode(MapRequestProto.MapRequest.RequestCode.MAP_INFO);
        OutputStream outputStream = socket.getOutputStream();
        InputStream inputStream = socket.getInputStream();
        request.build().writeDelimitedTo(outputStream);

        MapInfoProto.MapInfo info;
        long endTime = System.currentTimeMillis() + socket.getSoTimeout();
        while ((info = MapInfoProto.MapInfo.parseDelimitedFrom(inputStream)) == null) {
            if (socket.getSoTimeout() == 0) continue;
            if (System.currentTimeMillis() > endTime) throw new IOException("Timeout");
        }
        if (!info.getError().getCode().equals(MapErrorProto.MapError.ErrorCode.NONE)){
            throw new IOException(info.getError().getCode().toString());
        }
        return info;
    }

    public synchronized MapPackageProto.MapPackage getActiveMap() throws IOException {
        MapRequestProto.MapRequest.Builder request = MapRequestProto.MapRequest.newBuilder();
        request.setCode(MapRequestProto.MapRequest.RequestCode.GET_ACTIVE_MAP);
        OutputStream outputStream = socket.getOutputStream();
        InputStream inputStream = socket.getInputStream();
        request.build().writeDelimitedTo(outputStream);

        return getMap(inputStream);
    }

    public synchronized MapSlamProto.MapSlam getActiveMapSlam(int start) throws IOException {
        MapRequestProto.MapRequest.Builder request = MapRequestProto.MapRequest.newBuilder();
        request.setCode(MapRequestProto.MapRequest.RequestCode.GET_ACTIVE_MAP_SLAM);
        request.setOptInt(start);
        OutputStream outputStream = socket.getOutputStream();
        InputStream inputStream = socket.getInputStream();
        request.build().writeDelimitedTo(outputStream);

        return getMapSlam(inputStream);
    }

    public synchronized VacuumMap getActive() throws IOException {
        return new VacuumMap(getActiveMap(), getActiveMapSlam(0), 0);
    }

    public void updateActivePath(VacuumMap map) {
        int currentSlamLength = map.getPathSize();
        try {
            map.appendMapSlam(getActiveMapSlam(currentSlamLength));
        } catch (Exception ignored){
        }
    }

    public synchronized MapPackageProto.MapPackage getPreviousMap() throws IOException {
        MapRequestProto.MapRequest.Builder request = MapRequestProto.MapRequest.newBuilder();
        request.setCode(MapRequestProto.MapRequest.RequestCode.GET_PREVIOUS_MAP);
        OutputStream outputStream = socket.getOutputStream();
        InputStream inputStream = socket.getInputStream();
        request.build().writeDelimitedTo(outputStream);

        return getMap(inputStream);
    }

    public synchronized MapSlamProto.MapSlam getPreviousMapSlam(int start) throws IOException {
        MapRequestProto.MapRequest.Builder request = MapRequestProto.MapRequest.newBuilder();
        request.setCode(MapRequestProto.MapRequest.RequestCode.GET_PREVIOUS_MAP_SLAM);
        request.setOptInt(start);
        OutputStream outputStream = socket.getOutputStream();
        InputStream inputStream = socket.getInputStream();
        request.build().writeDelimitedTo(outputStream);

        return getMapSlam(inputStream);
    }

    public synchronized VacuumMap getPrevious() throws IOException {
        return new VacuumMap(getPreviousMap(), getPreviousMapSlam(0), 0);
    }

    public synchronized MapPackageProto.MapPackage getOldMap(String name) throws IOException {
        MapRequestProto.MapRequest.Builder request = MapRequestProto.MapRequest.newBuilder();
        request.setCode(MapRequestProto.MapRequest.RequestCode.GET_OLD_MAP);
        request.setOpt(name);
        OutputStream outputStream = socket.getOutputStream();
        InputStream inputStream = socket.getInputStream();
        request.build().writeDelimitedTo(outputStream);

        return getMap(inputStream);
    }

    public synchronized MapSlamProto.MapSlam getOldMapSlam(String name, int start) throws IOException {
        MapRequestProto.MapRequest.Builder request = MapRequestProto.MapRequest.newBuilder();
        request.setCode(MapRequestProto.MapRequest.RequestCode.GET_OLD_MAP_SLAM);
        request.setOpt(name);
        request.setOptInt(start);
        OutputStream outputStream = socket.getOutputStream();
        InputStream inputStream = socket.getInputStream();
        request.build().writeDelimitedTo(outputStream);

        return getMapSlam(inputStream);
    }

    public synchronized VacuumMap getOld(String name) throws IOException {
        return new VacuumMap(getOldMap(name), getOldMapSlam(name, 0), 0);
    }

    private MapPackageProto.MapPackage getMap(InputStream inputStream) throws IOException {
        if (inputStream == null) throw new IOException("InputStream null");
        MapPackageProto.MapPackage map;
        long endTime = System.currentTimeMillis() + socket.getSoTimeout();
        while ((map = MapPackageProto.MapPackage.parseDelimitedFrom(inputStream)) == null) {
            if (socket.getSoTimeout() == 0) continue;
            if (System.currentTimeMillis() > endTime) throw new IOException("Timeout");
        }
        if (!map.getError().getCode().equals(MapErrorProto.MapError.ErrorCode.NONE)){
            throw new IOException(map.getError().getCode().toString());
        }
        return map;
    }

    private MapSlamProto.MapSlam getMapSlam(InputStream inputStream) throws IOException {
        if (inputStream == null) throw new IOException("InputStream null");
        MapSlamProto.MapSlam slam;
        long endTime = System.currentTimeMillis() + socket.getSoTimeout();
        while ((slam = MapSlamProto.MapSlam.parseDelimitedFrom(inputStream)) == null) {
            if (socket.getSoTimeout() == 0) continue;
            if (System.currentTimeMillis() > endTime) throw new IOException("Timeout");
        }
        if (!slam.getError().getCode().equals(MapErrorProto.MapError.ErrorCode.NONE)){
            throw new IOException(slam.getError().getCode().toString());
        }
        return slam;
    }
}
