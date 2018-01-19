package com.pebble.getpebbledata;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPOutputStream;

/*import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.app.AlarmManager;*/
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.getpebble.android.kit.PebbleKit;

public class MyService extends Service {	
	
	public static final String TAG = "MyService";
	TimeZone tmadrid = TimeZone.getTimeZone("Europe/Madrid");	
	static String uuid= "7def5a6c-22af-45bd-b332-51ef6031d520"; //old pebble app uuid
	//static String uuid= "2a5f295a-3fab-4b9d-be97-30ab93609f84";
	//static String uuid= "3fc14e3e-6561-4e08-ae4a-6913d1137027"; //testing pebble app uuid	
	Timer timer;	
	TimerTask timerTask;		
	private long lastdata_time=0;
	Timer timer_peb_app;
	TimerTask timerTask_peb_app;
	private long current_time=0;	    
	private static Boolean wificon=false;
	private static Boolean timerstatus=false;
	static String pass="1314";
	static String op="ADD";
	private static final UUID PEBBLE_DATA_APP_UUID = UUID.fromString(uuid); 
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss"); //Set the format of the .txt file name.       
    private static final DateFormat DATE_FORMAT_GPS = new SimpleDateFormat("yyyyMMddHHmmssSSS"); //Set the format of the .txt file name.       
    private boolean registered = false;
    private SensorManager sensorManager;    
    private MySensorEventListener sensorEventListener;
    private String phoneAcc="";   
    private PebbleKit.PebbleDataLogReceiver mDataLogReceiver = null;    
    private String folderPath = Environment.getExternalStorageDirectory().getPath() + File.separator + "tmp" +  File.separator + "cache" + File.separator;
    //private String bkpfolder = Environment.getExternalStorageDirectory().getPath() + File.separator + "tmp" +  File.separator + "backup" + File.separator;
    //private String LogFolder = Environment.getExternalStorageDirectory().getPath() + File.separator + "tmp" +  File.separator + "cache" + File.separator;
    private String TextView2="";    
    private BroadcastReceiver updateReceiver;
    private boolean running=false;
    //final Context context=getApplicationContext();
	@Override
	public void onCreate() {
		super.onCreate();
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+0")); 			
		sensorEventListener = new MySensorEventListener(); 
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE); 		
		
        //***Create foreground notification to keep the service active and avoid been killed***
		Intent notificationIntent = new Intent(this, MainActivity.class);  
	    PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0); 
		Notification noti = new NotificationCompat.Builder(this)
			         .setContentTitle("Pebble Data")
			         .setContentText("Receiving movement data from Pebble...")
			         .setSmallIcon(R.drawable.ic_launcher)
			         .setContentIntent(pendingIntent)
			         //.setLargeIcon(R.drawable.ic_launcher)
			         .build(); // available from API level 4 and onwards
		startForeground(1, noti);      
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		boolean wifi_upload=intent.getBooleanExtra("WIFI_UPLOAD",false); //Get the status of the check box, if upload only under wifi
		//final String MacAddress=getMacAddress();
		//final String PebbleAddress=getPebbleAddress();
		restartPebApp();//set a timertask to restart the pebble app if it is not running
		//PebbleKit.startAppOnPebble(getApplicationContext(), PEBBLE_DATA_APP_UUID);
		//Data receiving function
        PebbleKit.PebbleDataLogReceiver mDataLogReceiver = new PebbleKit.PebbleDataLogReceiver(PEBBLE_DATA_APP_UUID) {    
        	Lock lock= new ReentrantLock(true); //Define a lock to avoid the concurrency problem when writing data to txt file    
        	int count=1;
       	 	//int count_gps=0; //update location information every second        	 	
    		String Location=getLocation();  
    		String t_GPS=Location.substring(Location.lastIndexOf(',') + 1);
    		String MacAddress=getMacAddress();
    		String PebbleAddress=getPebbleAddress();        	
    		StringBuffer sb = new StringBuffer();    		
    		long[] tmark=new long[750]; //array to hold the timestamp, used to identify the GPS information
    		int itmark=1;
       	 	@Override  
			public void receiveData(Context context, UUID logUuid, Long timestamp, Long tag, byte[] accdata) {          	 		 
       	 		//lastdata_time=System.currentTimeMillis();   //get the data coming time for restarting the pebble app    			       	 		
           		String dataString = null;
           		String UIString =null;           
           		//String loguuid = logUuid.toString();
           		//System.out.println("LOGUUID:"+loguuid+"timestamp:"+timestamp);
           		if (count%20==0){   	//update location information every 50/25 second        	
           			//Location=getLocation();
           			lastdata_time=System.currentTimeMillis();   //get the data coming time for restarting the pebble app 
           			//count_gps++;
           		} 	
    			try {						//Transform the format of received data from byte array to string.
    				dataString = new String(accdata, "UTF-8");   
    				//System.out.println("data:"+dataString);
    			} catch (UnsupportedEncodingException e) {
    				System.out.println("Error unencoding data:"+e.getMessage());
    				return;
    			}    			
    			dataString=dataString.substring(0, dataString.indexOf("}"))+ phoneAcc+","+t_GPS+",'peb':'"+PebbleAddress+"'}";
    			//dataString=dataString.substring(0, dataString.indexOf("}"))+ Location+phoneAcc+",'op':'"+op+"','us':'"+MacAddress+"','pb':'"+PebbleAddress+"','pass':'"+pass+"'}";	
    			//UIString ="<"+Integer.toString(count)+">:"+dataString.substring(0, dataString.indexOf(",'pass'"))+ "}";	//hide psw from the screen
    			//databuffer=databuffer+dataString+"\n";
    			sb.append(dataString);
    			sb.append("\n");    	
    			tmark[itmark-1]=timestamp; 
    			//System.out.println(tmark[itmark-1]);
    			if (count%750==0){    				
    				MacAddress=getMacAddress();
    				PebbleAddress=getPebbleAddress();
    				//Location=getLocation(); 
    				
    				String toplines="{'us':'pebble','pass':'pebble','db':'HumanBehavior','collection':'TremorRaw'}\n"+    						   						
    						"{'peb':'"+PebbleAddress+"','phone':'"+MacAddress+"','from':'"+getUintAsTimestampFromTo(tmark[0]-3600)+"','until':'"+getUintAsTimestampFromTo(tmark[itmark-1]-3600)+"'}\n"+
    						"{"+Location+"}\n";    				
    				String input=toplines+sb.toString(); 
    				
    				//***Create text file and write data into it***
    				System.out.println("writting to text file");
    				//System.out.println("count:"+count+"buffer:"+sb.toString());
        			lock.lock(); //Lock begin
        			FileWriter fw = null;		
        			String shortname=getUintAsTimestamp(timestamp);
        			String txtname=folderPath + shortname;
        			
        			File txtfile=new File(txtname+".txt");         			
        			if (txtfile.exists()){                 //check if txt file already existed        				
        				File ftxt=new File(folderPath);    //if exist count the number of files with this name
        				int ntxt=0;
        				for (File file : ftxt.listFiles()){
        					if (file.isFile() && (file.getName().startsWith(shortname)) && (file.getName().endsWith(".txt"))) { 
        						ntxt++; 
        					} 
        				}	  
        				txtname=txtname+Integer.toString(ntxt);
        			}
        			
        			try {
						fw = new FileWriter(txtname + ".txt", true);
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}        			
        			BufferedWriter bufferWritter = new BufferedWriter(fw);
        			try {
        				//bufferWritter.write(sb.toString()); 
        				bufferWritter.write(input);
        				bufferWritter.close();
        			} catch (IOException e) {
        				System.out.println("Error writing to and closing file:"+e.getMessage());
        				lock.unlock(); //Release lock
        				return;
        			}
        			lock.unlock();  //Release lock 
        			//databuffer="";
        			sb.delete(0, sb.length());
        			itmark=0;        			
        			Location=getLocation(); //update location information
        			//System.out.println("location.."+Location);
        			t_GPS=Location.substring(Location.lastIndexOf(',') + 1);
    			}	
    			//Update the UI     			
    			if (count%50==0){			//send the UI string back to update the UI every 2 seconds
    				//UIString ="<"+Integer.toString(count)+">:"+dataString.substring(0, dataString.indexOf(",'pass'"))+ "}";	//hide psw from the screen
    				UIString ="<"+Integer.toString(count)+">:"+dataString;
    				sendBroadcastMessage(UIString);           				
           		}     			
    			count++;
    			itmark++; 
    			
       	 	}   	 	
       	 	
       	 	@Override
       	 	public void onFinishSession(Context context, UUID logUuid, Long timestamp, Long tag) {
       	 		//super.onFinishSession(context, logUuid, timestamp, tag);           
       	 	}
       	 	
        }; 
        
        //********Begin receiving data*************
        if (mDataLogReceiver != null && !registered) {
        	registered = true; 	
        	try {
				PebbleKit.registerDataLogReceiver(this, mDataLogReceiver);				
				PebbleKit.requestDataLogsForApp(this, PEBBLE_DATA_APP_UUID);
				TextView2="Receiving Pebble data...";	
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				TextView2="No data received!!! \n There is an Exception:"+e.getMessage();				
				System.out.println("No data received! \n There is an Exception:"+e.getMessage());
			}
        } else {
        	TextView2="No data received!!! ";        	
        	System.out.println("No data received! \n");
        }         
        
        Sensor accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		sensorManager.registerListener(sensorEventListener, accelerometerSensor, SensorManager.SENSOR_DELAY_UI);  //FASTEST: 0ms; DELAY_GAME: 20ms; UI: 60ms; NORMAL: 200ms
		
		//select the upload method
		upload_mode(wifi_upload);		
				
		//***Monitor the Internet connection, update the upload method when connection changed
		//http://stackoverflow.com/questions/14364632/update-active-activity-ui-with-broadcastreceiver
		final boolean wifi_change=wifi_upload;
		updateReceiver=new BroadcastReceiver() {
	        @Override
	        public void onReceive(Context context, Intent intent) {
	        	upload_mode(wifi_change);	        	        	
	        }
	    };
	    IntentFilter updateIntentFilter=new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
	    registerReceiver(updateReceiver, updateIntentFilter);	    
		return super.onStartCommand(intent, flags, startId);
	}
	
	//Send UI information back to main activity
	public static final String ACTION_UI_BROADCAST="UI_TEXTVIEW_INFO",
							   DATA_STRING="textview_string",
							   TEXT_VIEW2="textview2";
	
	private void sendBroadcastMessage(String UIstring) {
        if (UIstring != null) {
            Intent intent = new Intent(ACTION_UI_BROADCAST);
            intent.putExtra(DATA_STRING, UIstring);       
            intent.putExtra(TEXT_VIEW2, TextView2);             
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }
	
	//Send restart app message
	private void RestartBroadcastMessage(long time_gap) {
        
            Intent intent = new Intent("APP_RESTART_BROADCAST");
            intent.putExtra("TIMEGAP", time_gap);                               
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        
    }
		
	@Override
	public void onDestroy() {
		super.onDestroy();
		System.out.println(TAG + "onDestroy() executed");
		registered = false;
		if (mDataLogReceiver != null) {        	
	            unregisterReceiver(mDataLogReceiver);
	            mDataLogReceiver = null;
	    }
		sensorManager.unregisterListener(sensorEventListener); 
	    timer.cancel();
	    //timer_peb_app.cancel();
	    
	    if (this.updateReceiver!=null)               //unregister BroadcastReceiver
	        unregisterReceiver(updateReceiver);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	//Set a timer to restart the pebble app 
	public void startPebApp(){
		PebbleKit.startAppOnPebble(getApplicationContext(), PEBBLE_DATA_APP_UUID);
		running=true;
		System.out.println("Pebble App started!");
	}
	public void closePebApp(){
		PebbleKit.closeAppOnPebble(getApplicationContext(), PEBBLE_DATA_APP_UUID);
		running=false;
		System.out.println("Pebble App closed!");
	}
    public void restartPebApp(){
    	timer_peb_app =new Timer();
    	System.out.println("restarttimer begin..");
    	ini_restartPebApp_Task();
    	timer_peb_app.schedule(timerTask_peb_app, 90000, 30000);
    }

    public void ini_restartPebApp_Task(){      	 
    	timerTask_peb_app = new TimerTask(){    		
    		public void run(){
    			//restart the pebble app    
    			final long lastdata=lastdata_time;
    			current_time=System.currentTimeMillis()-lastdata;
    			System.out.println("lastdata_time: "+lastdata+"time difference: "+Long.toString(current_time));
    			//if no data coming in more than 30 seconds, try to start the watch app
    			
    			if ((current_time>30000 && current_time< 90000) || lastdata==0){       				
    				startPebApp();    				   				
    			}    	
    			//if no data coming in more than 90 seconds, means there may be some problem of the watch app. try to kill the watch app and restart it.
    			if (current_time>90000 && current_time< 300000 && lastdata>0){     				
    				closePebApp();
    				startPebApp();    				    				
    			}  
    			//if there is no data coming even more time, try to restart the phone app to recover everything
    			if (current_time>300000 && lastdata>0){     				
    				RestartBroadcastMessage(current_time);    				    				
    			} 
    		}
    	};
    }
    
	//Secect data upload mode, only under wifi or always
	public void upload_mode (boolean wifi_upload){
		if (wifi_upload){
			uploadmode0();
		}else{
			uploadmode1();
		}		
	}
	public void uploadmode0(){     //upload only under wifi, check box checked
    	//if wifi is connected, start the upload process
		wificon=checkwifi();
        if (wificon==true){
        	if (timerstatus==false){
        		startTimer();
        	}     	         	        	
        }
        if (wificon==false ){
        	if (timerstatus==true){
        		timerTask.cancel();
        		timer.cancel();        		
            	timerstatus=false;
            	//textView1.setText("BLOCKED");            	
        	}        	        	
        }
    }
    public void uploadmode1(){	   //Always upload, check box unchecked.
    	if (timerstatus==false){
    		startTimer();         	
    	}
    } 
    
    //Timer for uploading data
	public void startTimer() {    
	    timerstatus=true;
	    timer = new Timer(); //set a new Timer    		    	
	    initializeTimerTask(); //initialize the TimerTask's job	    	
	    //schedule the timer, after the first 5000ms the TimerTask will run every 60000ms
	    timer.schedule(timerTask, 5000, 60000);     	
	}


	public void initializeTimerTask() {	    	
	    timerTask = new TimerTask() {    		
	    	public void run() {    			
	    		//***Get file list in the folder // stackoverflow.com/questions/8646984/how-to-list-files-in-an-android-directory
	            String folderpath = Environment.getExternalStorageDirectory().getPath() + File.separator + "tmp" +  File.separator + "cache";
                String bkpfolder = Environment.getExternalStorageDirectory().getPath() + File.separator + "tmp" +  File.separator + "backup";
				try {
					//File file[] = f.listFiles();
					File filegz[] = findergz(folderpath);   //get all the .gz file
		            if (filegz.length>0) {			// If there are .gz files, upload them		
						for (int j = 0; j < filegz.length; j++) {
							String datapathgz = bkpfolder + File.separator + filegz[j].getName();
                            File bkpfile = new File(datapathgz);
							//new RetrieveFeedTask().execute(datapathgz);
                            filegz[j].renameTo(bkpfile);
						}
					} else{
						try {
			            	File file[] = finder(folderpath);  //get all the .txt file	
							if (file.length > 0) {
								for (int i = 0; i < file.length; i++) //Send all the files to the server one by one.
								{
									//Log.d("Files", "FileName:" + file[i].getName());						
									boolean complete = isCompletelyWritten(file[i]); //Check if the file has completely written
									String srcpath = folderpath + File.separator + file[i].getName();
                                    String bkppath = bkpfolder + File.separator + file[i].getName();
									if (complete) {
										//Log.d("Files", "path" + datapath);
										//new RetrieveFeedTask().execute(datapath); //execute new thread 执行同步线程
										//Log.d("Files", "i:" + i);
                                        //compress the .txt file to .gz file
                                        String despath0 = srcpath.substring(0, srcpath.indexOf(".")) + ".gz";
                                        //String despath=datapath[0]+".gz";
                                        String gzfile = gzipFile(srcpath, despath0);
                                        File zip = new File(gzfile);
                                        String despath = bkpfolder + File.separator + zip.getName();
                                        File newzip = new File(despath);
                                        zip.renameTo(newzip);
									}
								}
							}
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							Log.d("Files", e.getLocalizedMessage() );
						}
					}			
		            	            	
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					Log.d("Files", e.getLocalizedMessage() );				
				}
	    	}
	      };
	    }


    //Gzip a text file http://examples.javacodegeeks.com/core-java/io/fileinputstream/compress-a-file-in-gzip-format-in-java/
    public String gzipFile(String source_filepath, String destinaton_zip_filepath) {
        System.out.println("Compressing "+ source_filepath+"...........");
        byte[] buffer = new byte[1024];
        File textfile = new File(source_filepath);
        if (textfile.exists() && textfile.length()>1000) {
            String gzfile = countgz(source_filepath);
            destinaton_zip_filepath = source_filepath.substring(0,
                    source_filepath.indexOf(".")) + "_" + gzfile + ".gz";
            //System.out.println("gzfile:"+gzfile);
            try {
                FileOutputStream fileOutputStream = new FileOutputStream(
                        destinaton_zip_filepath);
                GZIPOutputStream gzipOuputStream = new GZIPOutputStream(
                        fileOutputStream);
                FileInputStream fileInput = new FileInputStream(source_filepath);
                int bytes_read;
                while ((bytes_read = fileInput.read(buffer)) > 0) {
                    gzipOuputStream.write(buffer, 0, bytes_read);
                }
                try {
                    fileInput.close();
                } catch (Exception e) {
                    // TODO: handle exception
                }
                try {
                    gzipOuputStream.finish();
                    gzipOuputStream.close();
                } catch (Exception e) {
                    // TODO: handle exception
                }
                //System.out.println("The file was compressed successfully!");
                File gzf = new File(destinaton_zip_filepath);//check if the generated gzfile is larger than 1kb.
                if (gzf.length() > 1000) {
                    textfile.delete();
                    return destinaton_zip_filepath;
                } else {
                    gzf.delete();
                    return null;
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                return null;
            }
        }else{
            return null;
        }
    }

    public String countgz(String filepath){
        File txtfile = new File (filepath);
        String fullname = txtfile.getName();
        String folderpath = Environment.getExternalStorageDirectory().getPath() + File.separator + "tmp" +  File.separator + "cache"+File.separator;
        String firstname=fullname.substring(0, fullname.indexOf("."));
        //System.out.println("fullname:"+fullname+"folderpath:"+folderpath+"firstname:"+firstname);
        File gzf=new File(folderpath);
        int count=0;
        for (File file : gzf.listFiles()){
            if (file.isFile() && (file.getName().startsWith(firstname)) && (file.getName().endsWith(".gz"))) {
                count++;
            }
        }
        return Integer.toString(count);
    }
	//find all the .txt files in a folder. http://stackoverflow.com/questions/1384947/java-find-txt-files-in-specified-folder
	public File[] finder( String dirName){
		File dir = new File(dirName);
	   	return dir.listFiles(new FilenameFilter() { 
	         public boolean accept(File dir, String filename)
	              { return filename.endsWith(".txt"); }
	    } );
	}
	    
	//find .gz file
	public File[] findergz( String dirName){
	  	File dir = new File(dirName);
	   	return dir.listFiles(new FilenameFilter() { 
	         public boolean accept(File dir, String filename)
	              { return filename.endsWith(".gz"); }
	   	} );
	}
	    
	//Check if a file is been written.10 seconds since last modification.	    
	private boolean isCompletelyWritten(File file) {
	    long currenttime=System.currentTimeMillis();
	    long lastmodify=file.lastModified();
	    if (currenttime-lastmodify>(10000)){
	      	return true;
	    }else{
	     	return false;
	    }
	}
	 
	//Change the date format
	private String getUintAsTimestamp(Long uint) {    	
	  	//return DATE_FORMAT.format(new Date(uint.longValue() * 1000L)).toString();
		//uint=uint+tmadrid.getOffset(uint);
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+0"));
	   	return DATE_FORMAT.format(new Date(uint * 1000L)).toString();
	}   
	    
	//Change the date format
	private String getUintAsTimestampGPS(Long uint) {    	
	   	//return DATE_FORMAT.format(new Date(uint.longValue() * 1000L)).toString();
	   	//DATE_FORMAT_GPS.setTimeZone(TimeZone.getTimeZone("GMT+0")); //set timezone*******?
		//uint=uint+tmadrid.getOffset(uint); //added in the function
	   	return DATE_FORMAT_GPS.format(new Date(uint)).toString();
	}   
	
	//Date format for "from until"
		private String getUintAsTimestampFromTo(Long uint) {    	
		  	//return DATE_FORMAT.format(new Date(uint.longValue() * 1000L)).toString();
			//uint=uint+tmadrid.getOffset(uint);
			
		   	return DATE_FORMAT_GPS.format(new Date(uint * 1000L)).toString();
		} 	
	    
	//Get the GPS information from the phone. //Reference: http://blog.csdn.net/cjjky/article/details/6557561
	private String getLocation(){    	
	   	double latitude=0.0;
	   	double longitude =0.0;	
	   	double altitude =0.0;	
	   	float accuracy=0;
	   	long t_gps=0;	   	   	
	   	long utcTime = System.currentTimeMillis();
		//t_gps=utcTime+tmadrid.getOffset(utcTime);
	   	t_gps=utcTime;
	   	LocationManager locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
	   	LocationListener locationListener = new LocationListener() {    					
    		// Provider的状态在可用、暂时不可用和无服务三个状态直接切换时触发此函数
    		@Override
    		public void onStatusChanged(String provider, int status, Bundle extras) {    						
    		}    					
    		// Provider被enable时触发此函数，比如GPS被打开
    		@Override
    		public void onProviderEnabled(String provider) {    						
    		}    					
    		// Provider被disable时触发此函数，比如GPS被关闭 
    		@Override
    		public void onProviderDisabled(String provider) {    						
    		}    					
    		//当坐标改变时触发此函数，如果Provider传进相同的坐标，它就不会被触发 
    		@Override
    		public void onLocationChanged(Location location) {
    			/*if (location != null) {   
    				Log.e("Map", "Location changed : Lat: "  
    				+ location.getLatitude() + " Lng: "  + location.getLongitude()+ " Alt: "  
    				+ location.getAltitude()+" Acc: " + location.getAccuracy()+" t_gps:"+location.getTime());   
    			}	   */ 			
    			//t_gps=location.getTime(); //timestamp
    		}
    	};
		if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
			    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,300, 0,locationListener);  
				Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
				if(location != null){
					latitude = location.getLatitude();
					longitude = location.getLongitude();
					altitude = location.getAltitude();
					accuracy=location.getAccuracy();	
					//System.out.println("GPS"+String.valueOf(latitude));
					//t_gps=location.getTime();
					//t_gps=System.currentTimeMillis()+1*3600*1000;	
					//long utcTime = System.currentTimeMillis();
					//t_gps=utcTime+tmadrid.getOffset(utcTime);
				}
	    }else{	    		
	    		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,300, 0,locationListener);   
	    		Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);   
	    		if(location != null){   
	    			latitude = location.getLatitude(); //经度   
	    			longitude = location.getLongitude(); //纬度
	    			altitude=location.getAltitude(); //海拔
	    			accuracy=location.getAccuracy(); //精度, in meters
	    			//System.out.println("NETWORK"+String.valueOf(latitude));
	    			//t_gps=location.getTime(); //timestamp
	    			//t_gps=System.currentTimeMillis()+1*3600*1000;
	    			//long utcTime = System.currentTimeMillis();
					//t_gps=utcTime+tmadrid.getOffset(utcTime);
	    		}    
	    }
	    String location="'lat':"+String.valueOf(latitude)+",'lng':"+String.valueOf(longitude)+",'alt':"+String.valueOf(altitude)
	    					+",'acc':"+Float.toString(accuracy)+",'t_gps':"+getUintAsTimestampGPS(t_gps);
	    return location;    	
	}
	    
	//Get accelerometer values.  //Reference:http://blog.csdn.net/tangcheng_ok/article/details/6590493   
	private final class MySensorEventListener implements SensorEventListener {    
	    @Override    
	    public void onSensorChanged(SensorEvent event) {                  
	       if(event.sensor.getType()==Sensor.TYPE_ACCELEROMETER) { //得到加速度的值 
	            float x = event.values[0]/0.00981f;          
	            float y = event.values[1]/0.00981f;          
	            float z = event.values[2]/0.00981f;
	            //long t_acc=event.timestamp;	//nanoseconds since uptime
	            //change the event.timestamp to milliseconds 
	            //http://stackoverflow.com/questions/5500765/accelerometer-sensorevent-timestamp
	            //long timeInMillis = (new Date()).getTime() + (event.timestamp - System.nanoTime()) / 1000000L+1*3600*1000;
	            long utcTime = System.currentTimeMillis();	
	            long t_acc = utcTime;
	            //long t_acc = utcTime+tmadrid.getOffset(utcTime);	               
	            phoneAcc= ",'px':"+Float.toString(x)+",'py':"+Float.toString(y)+",'pz':"+Float.toString(z)+",'ta':'"+getUintAsTimestampGPS(t_acc)+"'";
	        }               
	    }     
	    @Override    
	    public void onAccuracyChanged(Sensor sensor, int accuracy) {    
	    }    
	}    
	    
	//Get MAC address of the phone, here we use the bluetooth MAC as the ID of the phone. 
	//Other method refer to cloudstack.blog.163.com/blog/static/1876981172012710823152/
	private String getMacAddress(){  
	    BluetoothAdapter m_BluetoothAdapter = null; // Local Bluetooth adapter 
	    m_BluetoothAdapter = BluetoothAdapter.getDefaultAdapter();      
	    String m_szBTMAC = m_BluetoothAdapter.getAddress();  
	    return m_szBTMAC;
	}  
	    
	//Get pebble MAC address
	private String getPebbleAddress(){
	    //获得BluetoothAdapter对象，该API是android 2.0开始支持的  
	    String pebbleAddress="";
	    BluetoothAdapter adapter = null;
	    adapter = BluetoothAdapter.getDefaultAdapter();          
	    Set<BluetoothDevice> devices = adapter.getBondedDevices();      	
	    if(devices.size()>0){          	
	        for(Iterator<BluetoothDevice> it = devices.iterator();it.hasNext();)
	            {  //get the address of all paired devices
	                BluetoothDevice device = (BluetoothDevice)it.next();                 	                
	                pebbleAddress = pebbleAddress + device.getAddress();  
	            } 	            
	    }else{  
	       pebbleAddress = "ERR_NO_DEVICE";  
	    }  
	    pebbleAddress= pebbleAddress.substring(0,17); //only keep the first one
	    return pebbleAddress;
	}

	//Check wifi connection
	private boolean checkwifi(){	        
		boolean wifistatus = false;
	    ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo[] netInfo = cm.getAllNetworkInfo();
	    for (NetworkInfo ni : netInfo) {
	      	if (ni.isConnected()){
	       		if (ni.getTypeName().equalsIgnoreCase("WIFI")){	        			
	       			wifistatus=true;
	       		}	        		  
	       	}
	    } 
	    return wifistatus;
	}
}

/*
//创建同步线程 http://stackoverflow.com/questions/6343166/android-os-networkonmainthreadexception
class RetrieveFeedTask extends AsyncTask<String, Void, Void> {
	protected Void doInBackground(String... datapath) {
		String srcpath=datapath[0];
		String filetype=srcpath.substring(srcpath.lastIndexOf(".")+1);
		//System.out.println("filetype:"+filetype);
		String despath0="";
		String despath=null;
		String bkpath="";
		String bkpfolder = Environment.getExternalStorageDirectory().getPath() + File.separator + "tmp" +  File.separator + "backup" + File.separator;
		File srcfile=new File(srcpath);
		if (srcfile.exists()) {
			if (filetype.equals("txt")) {
				despath0 = srcpath.substring(0, srcpath.indexOf(".")) + ".gz";
				//String despath=datapath[0]+".gz";
				despath = gzipFile(srcpath, despath0);
			} else if (filetype.equals("gz")) {
				despath = srcpath;
			}
		}
		if (despath!= null) {
			File gzfile = new File(despath);
			if (gzfile.exists()) {
				bkpath = bkpfolder + despath.substring(despath.lastIndexOf(File.separator) + 1); //backup file path. when upload failed, copy to this path
				File bkpfile = new File(bkpath);
				//http://stackoverflow.com/questions/2017414/post-multipart-request-with-android-sdk
				HttpClient client = new DefaultHttpClient();
				//client.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1); 
				try {
					//HttpPost httpPost = new HttpPost("http://apiinas02.etsii.upm.es/pebble/carga_pebble.py"); //check the upload result here: http://138.100.82.184/tmp/
					HttpPost httpPost = new HttpPost("http://pebble.etsii.upm.es/pebble/carga_pebble.py"); 
					//File gzfile = new File(despath);				
					MultipartEntityBuilder builder = MultipartEntityBuilder
							.create();
					builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
					builder.addBinaryBody("file", gzfile,
							ContentType.create("application/x-gzip"),
							gzfile.getName());
					HttpEntity entity = builder.build();
					httpPost.setEntity(entity);
					System.out.println("executing request for file"
							+ gzfile.getName() + gzfile.length()+httpPost.getRequestLine());
					HttpResponse response = client.execute(httpPost);
					HttpEntity resEntity = response.getEntity();
					String result = EntityUtils.toString(resEntity);
					//System.out.println(result + gzfile.getName());
					//System.out.println(result);			
					if (result.contains("OK")) {
						gzfile.delete();
						//gzfile.renameTo(bkpfile);
						System.out.println(result + gzfile.getName());
					} else {
						System.out.println("upload failed!!  gzfile:"+ gzfile.getName() + " size:"+ gzfile.length());
						gzfile.renameTo(bkpfile);
						System.out.println("                 bkpfile:"+bkpfile.getName()+ " size:"+ bkpfile.length()+"\nResult:"+result);
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					System.out.println("upload exception!!!!  gzfile:"+ gzfile.getName() + " size:" + gzfile.length());
					gzfile.renameTo(bkpfile);
					System.out.println("                      bkpfile:"+ bkpfile.getName() + " size:" + bkpfile.length());
				} finally {
					client.getConnectionManager().shutdown();
				}
			} else {
				System.out.println("ERROR: FILE NOT EXISTS!!!");
			}
		}
		return null;	
	}


}*/
