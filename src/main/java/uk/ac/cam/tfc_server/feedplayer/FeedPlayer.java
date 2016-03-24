package uk.ac.cam.tfc_server.feedplayer;

// *************************************************************************************************
// *************************************************************************************************
// *************************************************************************************************
// FeedPlayer.java
// Version 0.01
// Author: Ian Lewis ijl20@cam.ac.uk
//
// Forms part of the 'tfc_server' next-generation Realtime Intelligent Traffic Analysis system
//
// Reads GTFS-format binary files from the filesystem, broadcasts messages to eventbus
//
// FeedHandler will publish the feed data as a JSON string on eventbus "tfc.feedplayer.A"
//
// *************************************************************************************************
// *************************************************************************************************
// *************************************************************************************************

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.Handler;
import io.vertx.core.file.FileSystem;
import io.vertx.core.eventbus.EventBus;
//import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

import uk.ac.cam.tfc_server.util.GTFS;

// ********************************************************************************************
// ********************************************************************************************
// ********************************************************************************************
// Here is the main FeedPlayer class definition
// ********************************************************************************************
// ********************************************************************************************
// ********************************************************************************************

public class FeedPlayer extends AbstractVerticle {
  // Config vars
    private String MODULE_NAME; // from config()
    private String MODULE_ID; // from config()
    private String EB_SYSTEM_STATUS; // eventbus status reporting address

  String EB_FEEDPLAYER; // eventbus address for JSON feed position updates
    

    // eventbus address to replay messages
    private String EB_ADDRESS; // EB_FEEDPLAYER + "." + MODULE_ID;
    
    private String tfc_data_bin; // root of bin files

  private final int SYSTEM_STATUS_PERIOD = 10000; // publish status heartbeat every 10 s
  private final int SYSTEM_STATUS_AMBER_SECONDS = 15; // delay before flagging system as AMBER
  private final int SYSTEM_STATUS_RED_SECONDS = 25; // delay before flagging system as RED

  private EventBus eb = null;
    
      @Override
      public void start(Future<Void> fut) throws Exception {

        // load Zone initialization values from config()
        if (!get_config())
              {
                  fut.fail("FeedPlayer: failed to load initial config()");
              }

        System.out.println("FeedPlayer " + MODULE_NAME + "." + MODULE_ID + " started!");

        eb = vertx.eventBus();

        // send periodic "system_status" messages
        vertx.setPeriodic(SYSTEM_STATUS_PERIOD, id -> {
          eb.publish(EB_SYSTEM_STATUS,
                     "{ \"module_name\": \""+MODULE_NAME+"\"," +
                       "\"module_id\": \""+MODULE_ID+"\"," +
                       "\"status\": \"UP\"," +
                       "\"status_amber_seconds\": "+String.valueOf( SYSTEM_STATUS_AMBER_SECONDS ) + "," +
                       "\"status_red_seconds\": "+String.valueOf( SYSTEM_STATUS_RED_SECONDS ) +
                     "}" );
          });

        //debug all this file processing is temporary - should iterate over directory
        String filepath = "2016/03/07";
        String filename = "1457339414_2016-03-07-08-30-14";
        
        //        FileSystem fs = vertx.fileSystem();

        final String bin_path = tfc_data_bin+"/"+filepath;

        // read list of days filenames from directory
        vertx.fileSystem().readDir(bin_path, res -> {
                if (res.succeeded())
                    {
                        System.out.println(res.result().get(0));
                        // process the gtfs binary files, starting at file 0
                        try
                            {
                                process_gtfs_files(filepath, res.result(), 0);
                            }
                        catch (Exception e)
                            {
                                System.err.println("FeedPlayer: exception in process_gtfs_files()");
                            }
                    }
                else
                    {
                        System.err.println(res.cause());
                    }
            });
        
      } // end start()

    //
    void process_gtfs_files(String filepath, List<String> files, int i) throws Exception
    {
        //debug - arbitrary period constant, no confirmation zones are ready
        System.out.println("timer " + i);
        vertx.setTimer(3000, id -> {
                try
                    {
                        process_gtfs_files(filepath, files, i + 1);
                    }
                catch (Exception e)
                    {
                    }
            });
    }
    
    //debug this is just a placeholder to test compile
    void process_gtfs_file(String filename, String filepath) throws Exception
    {
        System.out.println("Reading "+tfc_data_bin+"/"+filepath+"/"+filename+".bin");

        // Read a file
        vertx.fileSystem().readFile(tfc_data_bin+"/"+filepath+"/"+filename+".bin", res -> {
                if (res.succeeded())
                {
                    try
                    {
                      JsonObject msg = GTFS.buf_to_json(res.result(), filename, filepath);
        
                      eb.publish(EB_ADDRESS, msg);
                      System.out.println("FeedPlayer " + MODULE_NAME + "." + MODULE_ID + " published to " + EB_ADDRESS);
                    } catch (Exception e)
                    {
                        System.err.println("FeedPlayer: exception in GTFS.buf_to_json()");
                    }
                } else
                {
                    System.err.println("FeedPlayer: " + res.cause());
                }
            });
        
    } // end process_gtfs()
  
    // Load initialization global constants defining this Zone from config()
    private boolean get_config()
    {
        // config() values needed by all TFC modules are:
        //   tfc.module_id - unique module reference to be used by this verticle
        //   eb.system_status - String eventbus address for system status messages

        MODULE_NAME = config().getString("module.name"); // "feedplayer"
        if (MODULE_NAME==null)
            {
                return false;
            }
        
        MODULE_ID = config().getString("module.id"); // A, B, ...

        EB_FEEDPLAYER = config().getString("eb.feedplayer");

        EB_ADDRESS = EB_FEEDPLAYER + "." + MODULE_ID;

        EB_SYSTEM_STATUS = config().getString("eb.system_status");

        
        //debug - this should be coming from a dynamic request, probably...
        tfc_data_bin = config().getString("feedplayer.files","");
        
        return true;
    }
    
} // end FeedPlayer class
