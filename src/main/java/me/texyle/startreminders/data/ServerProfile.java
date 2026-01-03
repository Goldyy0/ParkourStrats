package me.texyle.startreminders.data;

import java.util.ArrayList;

public class ServerProfile {
    private String id;
    private ArrayList<ParkourMap> maps = new ArrayList<ParkourMap>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ArrayList<ParkourMap> getMaps() {
        return maps;
    }

    public void setMaps(ArrayList<ParkourMap> maps) {
        this.maps = maps;
    }
}