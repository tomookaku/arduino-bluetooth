package org.surfgrid.twitterclient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.util.Xml;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.protocol.HTTP;
import org.xmlpull.v1.XmlPullParser;

//Twitterクライアント
public class TwitterClient extends Activity implements Runnable {

    private static final String TAG = "TwitterClient";

    public static CommonsHttpOAuthConsumer consumer;
    
    //情報
    private ListView listView;
    private static HashMap<String,Drawable> icons = new HashMap<String,Drawable>();
    private ArrayList<Status> timeline = new ArrayList<Status>();

	private InputStream mInputStream = null;
	private OutputStream mOutputStream = null;

	private static final int MESSAGE_TEMPERATURE = 2;

    public static final int MESSAGE_STATE_CHANGE = 5;
    public static final int MESSAGE_READ = 6;
    public static final int MESSAGE_WRITE = 7;
    public static final int MESSAGE_DEVICE_NAME = 8;
    public static final int MESSAGE_TOAST = 9;

    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    public static final byte LED_SERVO_COMMAND = 2;
	public static final byte RELAY_COMMAND = 3;

    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothService mBluetoothService = null;

    private boolean pending = false; 
    private int oldTemperature = 0;
    private int newTemperature = 0;
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        setContentView(R.layout.main);

        listView = (ListView)findViewById(R.id.ListView);

        final EditText status = (EditText)findViewById(R.id.UpdateEdit);
        final Button update = (Button)findViewById(R.id.UpdateButton);
        final Button reload = (Button)findViewById(R.id.ReloadButton);

        update.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				new Thread() {
				     @Override
				     public void run() {
				    	 update.setClickable(false);
				    	 reload.setClickable(false);
				    	 updateStatus(status.getText().toString());
				    	 reload.setClickable(true);
				    	 update.setClickable(true);
				     }
				}.start();
			}
        });
        
        reload.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
		    	 update.setClickable(false);
		    	 reload.setClickable(false);
		    	 updateTimeline();
		    	 reload.setClickable(true);
		    	 update.setClickable(true);
			}
        });

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
        }

        updateTimeline();

        startBluetooth();
    }

	@Override
	public Object onRetainNonConfigurationInstance() {
		return super.onRetainNonConfigurationInstance();
	}

    @Override
    public void onStart() {
		Log.d(TAG, "onStart");
        super.onStart();
    }
    
    @Override
	public void onResume() {
		Log.d(TAG, "onResume");
		super.onResume();

		if (mInputStream != null && mOutputStream != null) {
			return;
		}

		if (mBluetoothService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mBluetoothService.getState() == mBluetoothService.STATE_NONE) {
              // Start the Bluetooth services
            	mBluetoothService.start();
            }
        }
    }

	@Override
	public void onPause() {
		Log.d(TAG, "onPause");
		super.onPause();
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy");

		if (mBluetoothService != null)
			mBluetoothService.stop();
		
		mInputStream = null;
		mOutputStream = null;

		super.onDestroy();
		
		Process.killProcess(Process.myPid()); 
	}

	private void startBluetooth() {
		mHandler.postDelayed(new Runnable() {
	        public void run() {
	            // If BT is not on, request that it be enabled.
	            // setupChat() will then be called during onActivityResult
	            if (!mBluetoothAdapter.isEnabled()) {
	                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
	                startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
	            // Otherwise, setup the session
	            } else {
	                if (mBluetoothService == null)
	                	mBluetoothService = new BluetoothService(TwitterClient.this, mHandler);

	                // Launch the DeviceListActivity to see devices and do scan
	                Intent serverIntent = new Intent(TwitterClient.this, DeviceListActivity.class);
	                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
	            }
	        }
	    }, 1000);
	}
	
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult " + requestCode);
        Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                // Get the device MAC address
                String address = data.getExtras()
                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                // Get the BLuetoothDevice object
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                // Attempt to connect to the device
                mBluetoothService.connect(device);
            }
            else {
            	finish();
            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a session

                if (mBluetoothService == null)
                	mBluetoothService = new BluetoothService(this, mHandler);

                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
            } else {
                // User did not enable Bluetooth or an error occured
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, "Bluetooth was not enabled.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_TEMPERATURE:
				TemperatureMsg t = (TemperatureMsg)msg.obj;
				handleTemperatureMessage(t);
				break;

        	case MESSAGE_STATE_CHANGE:
        		Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
        		switch (msg.arg1) {
        		case BluetoothService.STATE_CONNECTED:
        	        Thread thread = new Thread(null, TwitterClient.this, "TwitterClient");
        			thread.start();

        			Log.d(TAG, "accessory opened");
        			break;
        		case BluetoothService.STATE_CONNECTING:
        			break;
        		case BluetoothService.STATE_LISTEN:
        			if (mBluetoothService == null)
                    	mBluetoothService = new BluetoothService(TwitterClient.this, mHandler);

                    // Launch the DeviceListActivity to see devices and do scan
                    Intent serverIntent = new Intent(TwitterClient.this, DeviceListActivity.class);
                    startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
        			break;
        		case BluetoothService.STATE_NONE:
        			break;
        		}
        		break;
        	
        	case MESSAGE_DEVICE_NAME:
        		Toast.makeText(getApplicationContext(), "Connected to "
        				+ msg.getData().getString(DEVICE_NAME), Toast.LENGTH_SHORT).show();
        		break;

        	case MESSAGE_TOAST:
        		Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
        				Toast.LENGTH_SHORT).show();
        		break;
			}
		}
	};

	private int composeInt(byte hi, byte lo) {
		int val = (int) hi & 0xff;
		val *= 256;
		val += (int) lo & 0xff;
		return val;
	}

	protected class TemperatureMsg {
		private int temperature;

		public TemperatureMsg(int temperature) {
			this.temperature = temperature;
		}

		public int getTemperature() {
			return temperature;
		}
	}

	protected void handleTemperatureMessage(TemperatureMsg t) {
		if (pending)
			return;

		newTemperature = t.getTemperature();
		if (newTemperature != oldTemperature) {
			oldTemperature = newTemperature;

			pending = true;
			
			mHandler.postDelayed(new Runnable() {
				public void run() {
					// Twitterに現在の温度を1分毎に投稿する
					double voltagemv = newTemperature * 4.9;
					double ambientTemperatureC = (voltagemv * 20.0) / 1024.0;

					SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
					updateStatus("SBS-BT001: " + sdf.format(new Date()) + " [" + (new DecimalFormat("###.0")).format(ambientTemperatureC) + " 度]");
					pending = false;
				}
			}, 60000);
		}
	}

	public void run() {
		int ret = 0;
		byte[] buffer = new byte[3];
		int i;

		while (ret >= 0) {
			try {
				if (mInputStream != null) {
					int length = buffer.length;
					int offset = 0;
					while(offset < buffer.length) {
						ret = mInputStream.read(buffer, offset, length);
						offset += ret;
						length -= ret;
					}
					ret = buffer.length;
				}
				else {
	                mInputStream = mBluetoothService.getInputStream();
	    			mOutputStream = mBluetoothService.getOutputStream();
				}
			} catch (IOException e) {
				if (mInputStream != null) {
					if (mBluetoothService != null) mBluetoothService.stop2();
				}
                break;
			}

			i = 0;
			while (i < ret) {
				int len = ret - i;
				
				switch (buffer[i]) {
				case 0x4:
					if (len >= 3) {
						Message m = Message.obtain(mHandler,
								MESSAGE_TEMPERATURE);
						m.obj = new TemperatureMsg(composeInt(buffer[i + 1],
								buffer[i + 2]));
						mHandler.sendMessage(m);
					}
					i += 3;
					break;

				default:
					i = len;
					break;
				}
			}
		}
	}

	//タイムラインの更新
    private void updateTimeline() {
    	Log.d(TAG, "updateTimeline");
        timeline = new ArrayList<Status>();
        Status status =null;
        String tagName=null;
        InputStream in=null;
        try {
            //接続
            HttpGet httpGet=new HttpGet(
                "http://twitter.com/statuses/friends_timeline.xml"); 
            consumer.sign(httpGet);
            DefaultHttpClient http=new DefaultHttpClient();
            HttpResponse execute=http.execute(httpGet);
            
            //XMLのパース処理(4)
            in=execute.getEntity().getContent();
            final XmlPullParser parser=Xml.newPullParser();
            parser.setInput(new InputStreamReader(in));
            while (true) {
                int type=parser.next();
                //ドキュメント開始
                if (type==XmlPullParser.START_DOCUMENT) {
                } 
                //タグ開始
                else if (type==XmlPullParser.START_TAG) {
                    tagName=parser.getName();
                    if (tagName.equals("status")) {
                        status=new Status();
                        timeline.add(status);
                    }
                } 
                //テキスト
                else if (type==XmlPullParser.TEXT) {
                    if (parser.getText().trim().length()==0) {
                    } else if (tagName.equals("screen_name")) {
                        status.name=parser.getText();
                    } else if (tagName.equals("text")) {
                        status.text=parser.getText();
                    } else if (tagName.equals("profile_image_url")) {
                        status.iconURL=parser.getText();
                    }
                } 
                //タグ終了
                else if (type==XmlPullParser.END_TAG) {
                } 
                //ドキュメント終了
                else if (type==XmlPullParser.END_DOCUMENT) {
                    break;
                }                
            }

            //切断
            in.close();

            //更新
            for (int i=0;i<timeline.size();i++) {
                timeline.get(i).icon=readIcon(timeline.get(i).iconURL);
            }
            listView.setAdapter(new TwitterAdapter(this));
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(),Toast.LENGTH_LONG).show();
        	e.printStackTrace();
        }
    }
    
    //アイコンの読み込み
    private Drawable readIcon(String url) throws Exception {
        Drawable drawable = icons.get(url);
        if (drawable!=null) return drawable;
        byte[] data=http2data(url);
        Bitmap bmp=BitmapFactory.decodeByteArray(data,0,data.length);
        drawable=new BitmapDrawable(bmp);
        icons.put(url,drawable);
        return drawable;
    }

    //HTTP通信
    private byte[] http2data(String path) throws Exception {
        int size;
        byte[] w = new byte[1024]; 
        HttpURLConnection c = null;
        InputStream in = null;
        ByteArrayOutputStream out = null;
        try {
            //HTTP接続のオープン
            URL url=new URL(path);
            c=(HttpURLConnection)url.openConnection();
            c.setRequestMethod("GET");
            c.connect();
            in=c.getInputStream();
            
            //バイト配列の読み込み
            out=new ByteArrayOutputStream();
            while (true) {
                size=in.read(w);
                if (size<=0) break;
                out.write(w,0,size);
            }
            out.close();

            //HTTP接続のクローズ
            in.close();
            c.disconnect();
            return out.toByteArray();
        } catch (Exception e) {
            try {
                if (c!=null) c.disconnect();
                if (in!=null) in.close();
                if (out!=null) out.close();
            } catch (Exception e2) {
            }
            throw e;
        }
    }
    
    //Twitterアダプタの生成(2)
    public class TwitterAdapter extends BaseAdapter {
        private Context context;

        //コンストラクタ
        public TwitterAdapter(Context c) {
            context=c;
        }

        //項目数の取得
        public int getCount() {
            return timeline.size();
        }

        //項目の取得
        public Object getItem(int position) {
            return timeline.get(position);
        }

        //項目IDの取得
        public long getItemId(int position) {
            return position;
        }

        //ビューの取得
        public View getView(int position,
            View convertView,ViewGroup parent) {
            TextView textView=new TextView(context);
            textView.setTextSize(12.0f);
            textView.setCompoundDrawablesWithIntrinsicBounds(
                timeline.get(position).icon,null,null,null);
            textView.setText("["+timeline.get(position).name+"]\n"+
                timeline.get(position).text);
            return textView;
        }
    }

    private void updateStatus(String status) {
    	Log.d(TAG, "updateStatus: " + status);
        try {
            HttpPost post = new HttpPost("http://twitter.com/statuses/update.xml"); 
            final List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("status", status));  
            post.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));  
            post.getParams().setBooleanParameter(
                CoreProtocolPNames.USE_EXPECT_CONTINUE, false);
            consumer.sign(post);
            DefaultHttpClient http = new DefaultHttpClient();
            http.execute(post);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

  //ステータス
    public class Status {
        public String   name;   //名前
        public String   text;   //テキスト
        public String   iconURL;//アイコンURL
        public Drawable icon;   //アイコン
    }
}
