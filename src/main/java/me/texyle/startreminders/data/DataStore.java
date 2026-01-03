package me.texyle.startreminders.data;

import java.util.ArrayList;

public class DataStore {
    private ArrayList<ServerProfile> servers = new ArrayList<ServerProfile>();

    public ArrayList<ServerProfile> getServers() {
        return servers;
    }

    public void setServers(ArrayList<ServerProfile> servers) {
        this.servers = servers;
    }
}