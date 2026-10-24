package mw.path;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.QueryParam;
import javax.ws.rs.DefaultValue;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import mw.client.MWWebServiceException;
import mw.client.MWRegistryClient;
import mw.client.MWWebServiceClient;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
@Path("/path")
public class MWPathServer {
    private static MWRegistryClient registry;
    @GET
    @Path("/{startID}/{endID}")
    @Produces(MediaType.APPLICATION_JSON)
    public MWPath getPath(
            @PathParam("startID") String startID,
            @PathParam("endID") String endID,
            @QueryParam("batching") @DefaultValue("false") boolean batching) {

        if (batching) {
            return getPathWithBatching(startID, endID);
        } else {
            return getPathWithoutBatching(startID, endID);
        }
    }


        private MWPath getPathWithoutBatching(String startID, String endID) {
            int numberOfCalls = 0;

            if (startID.equals(endID)) {
                MWPath result = new MWPath();
                result.path = new String[]{startID};
                result.numberOfIDs = 1;
                result.numberOfCalls = 0;
                return result;
            }

            MWWebServiceClient facebook = new MWWebServiceClient();

            String[] startFriends;
            String[] endFriends;

            try {
                startFriends = facebook.getFriends(startID);
                numberOfCalls++;

                endFriends = facebook.getFriends(endID);
                numberOfCalls++;
            } catch (MWWebServiceException e) {
                e.printStackTrace();
                return null;
            }

            Map<String, Collection<String>> friendships = new HashMap<>();
            friendships.put(startID, Arrays.asList(startFriends));
            friendships.put(endID, Arrays.asList(endFriends));

            Set<String> startCircle = new HashSet<>(Arrays.asList(startFriends));
            Set<String> endCircle = new HashSet<>(Arrays.asList(endFriends));

            startCircle.add(startID);
            endCircle.add(endID);

            Set<String> intersection = new HashSet<>(startCircle);
            intersection.retainAll(endCircle);

            boolean connected = !intersection.isEmpty();

            Set<String> startFrontier = new HashSet<>(Arrays.asList(startFriends));
            Set<String> endFrontier = new HashSet<>(Arrays.asList(endFriends));

            while (!connected) {
                Set<String> nextStartFrontier = new HashSet<>();
                Set<String> nextEndFrontier = new HashSet<>();

                // 1. 起点方向：逐个 GET
                for (String id : startFrontier) {
                    Collection<String> friends = friendships.get(id);

                    if (friends == null) {
                        try {
                            friends = Arrays.asList(facebook.getFriends(id));
                            numberOfCalls++;
                        } catch (MWWebServiceException e) {
                            e.printStackTrace();
                            return null;
                        }

                        friendships.put(id, friends);
                    }

                    for (String friend : friends) {
                        if (startCircle.add(friend)) {
                            nextStartFrontier.add(friend);
                        }
                    }
                }

                intersection = new HashSet<>(startCircle);
                intersection.retainAll(endCircle);
                connected = !intersection.isEmpty();

                if (connected) {
                    break;
                }

                // 2. 终点方向：逐个 GET
                for (String id : endFrontier) {
                    Collection<String> friends = friendships.get(id);

                    if (friends == null) {
                        try {
                            friends = Arrays.asList(facebook.getFriends(id));
                            numberOfCalls++;
                        } catch (MWWebServiceException e) {
                            e.printStackTrace();
                            return null;
                        }

                        friendships.put(id, friends);
                    }

                    for (String friend : friends) {
                        if (endCircle.add(friend)) {
                            nextEndFrontier.add(friend);
                        }
                    }
                }

                intersection = new HashSet<>(startCircle);
                intersection.retainAll(endCircle);
                connected = !intersection.isEmpty();

                startFrontier = nextStartFrontier;
                endFrontier = nextEndFrontier;

                if (!connected
                        && startFrontier.isEmpty()
                        && endFrontier.isEmpty()) {
                    break;
                }
            }

            MWPath result = new MWPath();
            result.numberOfCalls = numberOfCalls;

            Set<String> allIDs = new HashSet<>(friendships.keySet());

            for (Collection<String> friends : friendships.values()) {
                allIDs.addAll(friends);
            }

            result.numberOfIDs = allIDs.size();

            if (connected) {
                result.path = MWDijkstra.getShortestPath(
                        startID, endID, friendships);
            } else {
                result.path = new String[0];
            }

            return result;
        }
    


        private MWPath getPathWithBatching(String startID, String endID) {
    int numberOfCalls = 0;

    if (startID.equals(endID)) {
        MWPath result = new MWPath();
        result.path = new String[]{startID};
        result.numberOfIDs = 1;
        result.numberOfCalls = 0;
        return result;
    }

    MWWebServiceClient facebook = new MWWebServiceClient();

    // 1. 初始化：一次 POST 查询起点和终点
    Map<String, HashSet<String>> initialFriends;

    try {
        initialFriends = facebook.getFriends(
                new String[]{startID, endID});
        numberOfCalls++;
    } catch (MWWebServiceException e) {
        e.printStackTrace();
        return null;
    }

    String[] startFriends =
            initialFriends.get(startID).toArray(new String[0]);
    String[] endFriends =
            initialFriends.get(endID).toArray(new String[0]);

    Map<String, Collection<String>> friendships = new HashMap<>();
    friendships.put(startID, Arrays.asList(startFriends));
    friendships.put(endID, Arrays.asList(endFriends));

    Set<String> startCircle = new HashSet<>(Arrays.asList(startFriends));
    Set<String> endCircle = new HashSet<>(Arrays.asList(endFriends));

    startCircle.add(startID);
    endCircle.add(endID);

    Set<String> intersection = new HashSet<>(startCircle);
    intersection.retainAll(endCircle);

    boolean connected = !intersection.isEmpty();

    Set<String> startFrontier = new HashSet<>(Arrays.asList(startFriends));
    Set<String> endFrontier = new HashSet<>(Arrays.asList(endFriends));

    while (!connected) {
        Set<String> nextStartFrontier = new HashSet<>();
        Set<String> nextEndFrontier = new HashSet<>();

        // 2. 起点方向：一次 POST 查询整批未缓存的 ID
        Set<String> uncachedStartIDs = new HashSet<>(startFrontier);
        uncachedStartIDs.removeAll(friendships.keySet());

        if (!uncachedStartIDs.isEmpty()) {
            try {
                Map<String, HashSet<String>> batchFriends =
                        facebook.getFriends(
                                uncachedStartIDs.toArray(new String[0]));

                friendships.putAll(batchFriends);
                numberOfCalls++;
            } catch (MWWebServiceException e) {
                e.printStackTrace();
                return null;
            }
        }

        for (String id : startFrontier) {
            Collection<String> friends = friendships.get(id);

            for (String friend : friends) {
                if (startCircle.add(friend)) {
                    nextStartFrontier.add(friend);
                }
            }
        }

        intersection = new HashSet<>(startCircle);
        intersection.retainAll(endCircle);
        connected = !intersection.isEmpty();

        if (connected) {
            break;
        }

        // 3. 终点方向：一次 POST 查询整批未缓存的 ID
        Set<String> uncachedEndIDs = new HashSet<>(endFrontier);
        uncachedEndIDs.removeAll(friendships.keySet());

        if (!uncachedEndIDs.isEmpty()) {
            try {
                Map<String, HashSet<String>> batchFriends =
                        facebook.getFriends(
                                uncachedEndIDs.toArray(new String[0]));

                friendships.putAll(batchFriends);
                numberOfCalls++;
            } catch (MWWebServiceException e) {
                e.printStackTrace();
                return null;
            }
        }

        for (String id : endFrontier) {
            Collection<String> friends = friendships.get(id);

            for (String friend : friends) {
                if (endCircle.add(friend)) {
                    nextEndFrontier.add(friend);
                }
            }
        }

        intersection = new HashSet<>(startCircle);
        intersection.retainAll(endCircle);
        connected = !intersection.isEmpty();

        startFrontier = nextStartFrontier;
        endFrontier = nextEndFrontier;

        if (!connected
                && startFrontier.isEmpty()
                && endFrontier.isEmpty()) {
            break;
        }
    }

    // 4. 构造结果
    MWPath result = new MWPath();
    result.numberOfCalls = numberOfCalls;

    Set<String> allIDs = new HashSet<>(friendships.keySet());

    for (Collection<String> friends : friendships.values()) {
        allIDs.addAll(friends);
    }

    result.numberOfIDs = allIDs.size();

    if (connected) {
        result.path = MWDijkstra.getShortestPath(
                startID, endID, friendships);
    } else {
        result.path = new String[0];
    }

    try{
        registry.putValue("gruppe6", "path-optimize",
            "number-of-ids",
            String.valueOf(result.getNumberOfIDs()));

        registry.putValue("gruppe6", "path-optimize",
            "number-of-calls",
            String.valueOf(result.getNumberOfCalls()));
    }catch(Exception e){
        System.out.println("Could not put values to registry.");
        e.printStackTrace();
    }

    return result;
}
    

    public static void main(String[] args) {
        
        URI uri = URI.create("http://localhost:8080/");
        registry = new MWRegistryClient();
        try {
            registry.createService("gruppe6", "path-optimize");
            registry.putValue("gruppe6", "path-optimize",
                    "address", uri.toString());
        } catch (Exception e) {
            System.out.println("Service already exists or could not be created.");
            e.printStackTrace();
        }
        
        ResourceConfig config = new ResourceConfig(MWPathServer.class);
        HttpServer server = GrizzlyHttpServerFactory.createHttpServer(uri, config);
        System.out.println("Path Server running at " + uri);
    }
}

