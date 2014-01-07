/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.DemoKit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.content.Intent;
import android.os.Process;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.Toast;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

public class DemoKitActivity extends Activity implements Runnable {
	private static final String TAG = "DemoKit";

	private InputStream mInputStream;
	private OutputStream mOutputStream;

	private static final int MESSAGE_SWITCH = 1;
	private static final int MESSAGE_TEMPERATURE = 2;
	private static final int MESSAGE_LIGHT = 3;
	private static final int MESSAGE_JOY = 4;

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 5;
    public static final int MESSAGE_READ = 6;
    public static final int MESSAGE_WRITE = 7;
    public static final int MESSAGE_DEVICE_NAME = 8;
    public static final int MESSAGE_TOAST = 9;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    public static final byte LED_SERVO_COMMAND = 2;
	public static final byte RELAY_COMMAND = 3;

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    // Local Bluetooth adapter
    BluetoothAdapter mBluetoothAdapter = null;
    BluetoothService mBluetoothService = null;

    protected class SwitchMsg {
		private byte sw;
		private byte state;

		public SwitchMsg(byte sw, byte state) {
			this.sw = sw;
			this.state = state;
		}

		public byte getSw() {
			return sw;
		}

		public byte getState() {
			return state;
		}
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

	protected class LightMsg {
		private int light;

		public LightMsg(int light) {
			this.light = light;
		}

		public int getLight() {
			return light;
		}
	}

	protected class JoyMsg {
		private int x;
		private int y;

		public JoyMsg(int x, int y) {
			this.x = x;
			this.y = y;
		}

		public int getX() {
			return x;
		}

		public int getY() {
			return y;
		}
	}
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		enableControls(false);

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
        }
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
/*
		if (mAccessory != null) {
			return mAccessory;
		} else {
*/
			return super.onRetainNonConfigurationInstance();
//		}
	}

    @Override
    public void onStart() {
		Log.d(TAG, "onStart");
        super.onStart();

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, setup the session
        } else {
            if (mBluetoothService == null)
            	mBluetoothService = new BluetoothService(this, mHandler);

            // Launch the DeviceListActivity to see devices and do scan
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
        }
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
		if (mBluetoothService != null) mBluetoothService.stop();
		mInputStream = null;
		mOutputStream = null;
		super.onPause();
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy");
		super.onDestroy();
		
		Process.killProcess(Process.myPid()); 
	}

	protected void enableControls(boolean enable) {
	}

	private int composeInt(byte hi, byte lo) {
		int val = (int) hi & 0xff;
		val *= 256;
		val += (int) lo & 0xff;
		return val;
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
/*
				mHandler.post(new Runnable() {
			        public void run() {
						enableControls(false);

		                // Launch the DeviceListActivity to see devices and do scan
		                Intent serverIntent = new Intent(DemoKitActivity.this, DeviceListActivity.class);
		                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
			        }
			    });
*/
                break;
			}

			i = 0;
			while (i < ret) {
				int len = ret - i;

				System.out.println("buffer[0]: " + buffer[0]);
				System.out.println("buffer[1]: " + buffer[1]);
				System.out.println("buffer[2]: " + buffer[2]);
				
				switch (buffer[i]) {
				case 0x1:
					if (len >= 3) {
						Message m = Message.obtain(mHandler, MESSAGE_SWITCH);
						m.obj = new SwitchMsg(buffer[i + 1], buffer[i + 2]);
						mHandler.sendMessage(m);
					}
					i += 3;
					break;

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

				case 0x5:
					if (len >= 3) {
						Message m = Message.obtain(mHandler, MESSAGE_LIGHT);
						m.obj = new LightMsg(composeInt(buffer[i + 1],
								buffer[i + 2]));
						mHandler.sendMessage(m);
					}
					i += 3;
					break;

				case 0x6:
					if (len >= 3) {
						Message m = Message.obtain(mHandler, MESSAGE_JOY);
						m.obj = new JoyMsg(buffer[i + 1], buffer[i + 2]);
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

	Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_SWITCH:
				SwitchMsg o = (SwitchMsg) msg.obj;
				handleSwitchMessage(o);
				break;

			case MESSAGE_TEMPERATURE:
				TemperatureMsg t = (TemperatureMsg) msg.obj;
				handleTemperatureMessage(t);
				break;

			case MESSAGE_LIGHT:
				LightMsg l = (LightMsg) msg.obj;
				handleLightMessage(l);
				break;

			case MESSAGE_JOY:
				JoyMsg j = (JoyMsg) msg.obj;
				handleJoyMessage(j);
				break;

        	case MESSAGE_STATE_CHANGE:
        		Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
        		switch (msg.arg1) {
        		case BluetoothService.STATE_CONNECTED:
        	        Thread thread = new Thread(null, DemoKitActivity.this, "DemoKit");
        			thread.start();

        			Log.d(TAG, "accessory opened");
        			enableControls(true);
        			break;
        		case BluetoothService.STATE_CONNECTING:
        			break;
        		case BluetoothService.STATE_LISTEN:
        			enableControls(false);

        			if (mBluetoothService == null)
                    	mBluetoothService = new BluetoothService(DemoKitActivity.this, mHandler);

                    // Launch the DeviceListActivity to see devices and do scan
                    Intent serverIntent = new Intent(DemoKitActivity.this, DeviceListActivity.class);
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

	public void sendCommand(byte command, byte target, int value) {
		byte[] buffer = new byte[3];
		if (value > 255)
			value = 255;

		buffer[0] = command;
		buffer[1] = target;
		buffer[2] = (byte) value;
		if (mOutputStream != null && buffer[1] != -1) {
			try {
				Log.i(TAG, "write command: " + buffer[0]);
				Log.i(TAG, "write target: " + buffer[1]);
				Log.i(TAG, "write value: " + buffer[2]);
				mOutputStream.write(buffer);
			} catch (IOException e) {
				Log.e(TAG, "write failed", e);

				if (mInputStream != null) {
					if (mBluetoothService != null) mBluetoothService.stop2();
				}
/*
				enableControls(false);

                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(DemoKitActivity.this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
*/
			}
		}
	}

	protected void handleJoyMessage(JoyMsg j) {
	}

	protected void handleLightMessage(LightMsg l) {
	}

	protected void handleTemperatureMessage(TemperatureMsg t) {
	}

	protected void handleSwitchMessage(SwitchMsg o) {
	}

	public void onStartTrackingTouch(SeekBar seekBar) {
	}

	public void onStopTrackingTouch(SeekBar seekBar) {
	}

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
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}
