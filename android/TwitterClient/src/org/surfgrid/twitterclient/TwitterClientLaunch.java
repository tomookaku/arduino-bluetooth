package org.surfgrid.twitterclient;

import oauth.signpost.OAuthProvider;
import oauth.signpost.basic.DefaultOAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

// OAuth認証クラス
public class TwitterClientLaunch extends Activity {
	static final String TAG = "TwitterClientLaunch";

    private final static String 
    	CONSUMER_KEY = "<あなたのアプリのコンシューマーキー Consumer key>",
    	CONSUMER_SECRET = "<あなたのアプリのシークレット Consumer secret>";
    private final String CALLBACKURL = "myapp://twitterclient";

    private OAuthProvider provider;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        //レイアウト
        setContentView(R.layout.launch);

        //認証
        doOauth(false);
	}
    
    //OAuth認証
    private void doOauth(boolean setup) {
        try {
            // OAuthコンシューマーの生成
        	TwitterClient.consumer = new CommonsHttpOAuthConsumer(CONSUMER_KEY, CONSUMER_SECRET);

        	provider = new DefaultOAuthProvider(
                "https://api.twitter.com/oauth/request_token",
                "https://api.twitter.com/oauth/access_token",
                "https://api.twitter.com/oauth/authorize");
            
            //トークンの読込
            SharedPreferences pref = getSharedPreferences("token", MODE_PRIVATE);
            String token = pref.getString("token", "");
            String tokenSecret = pref.getString("tokenSecret", "");
            
            //認証済み確認
            if (!setup && token.length() > 0 && tokenSecret.length() > 0) {
                //認証済み
            	TwitterClient.consumer.setTokenWithSecret(token, tokenSecret);
                
            	Intent intent = new Intent(this, TwitterClient.class);
        		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
        				| Intent.FLAG_ACTIVITY_CLEAR_TOP);
        		try {
        			startActivity(intent);
        		} catch (ActivityNotFoundException e) {
        			Log.e(TAG, "unable to start DemoKit activity", e);
        		}
        		finish();
            } 
            //認証処理のブラウザ起動
            else {
                String url = provider.retrieveRequestToken(TwitterClient.consumer, CALLBACKURL);
                Log.d(TAG, "doOauth url: " + url);
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        } catch (Exception e) {
            Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }
    
    //認証完了処理
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        
        Uri uri = intent.getData();

        Log.d(TAG, "onNewIntent uri: " + uri);
        
        if (uri != null && uri.toString().startsWith(CALLBACKURL)) {
            String verifier = uri.getQueryParameter(oauth.signpost.OAuth.OAUTH_VERIFIER);
            try {
                provider.retrieveAccessToken(TwitterClient.consumer, verifier);

                //トークンの書き込み
                SharedPreferences pref=getSharedPreferences("token", MODE_PRIVATE);
                SharedPreferences.Editor editor=pref.edit();
                editor.putString("token", TwitterClient.consumer.getToken());
                editor.putString("tokenSecret", TwitterClient.consumer.getTokenSecret());
                editor.commit();

        		try {
            		intent = new Intent(this, TwitterClient.class);
            		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        			startActivity(intent);
        		} catch (ActivityNotFoundException e) {
        			Log.e(TAG, "unable to start activity", e);
        		}
        		finish();
            } catch(Exception e){
                Toast.makeText(this, e.getMessage(),Toast.LENGTH_LONG).show();
            }
        }
    }
}

