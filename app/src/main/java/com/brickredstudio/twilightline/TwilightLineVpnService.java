package com.brickredstudio.twilightline;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import engine.Engine;

public class TwilightLineVpnService extends VpnService
{
    public static final int MESSAGE_START_PROXY_REQUEST = 1;
    public static final int MESSAGE_START_PROXY_RESPONSE = 2;
    public static final int MESSAGE_STOP_PROXY_REQUEST = 3;
    public static final int MESSAGE_STOP_PROXY_RESPONSE = 4;

    private static final int VPN_MTU = 1500;
    private static final String VPN_TUN_DEVICE_IPV4 = "172.27.0.1";

    private Messenger selfMessenger = null;
    private Messenger clientMessenger = null;
    private ParcelFileDescriptor vpnFileDescriptor = null;
    private Process twilightLineClientProcess = null;

    public TwilightLineVpnService()
    {
        this.selfMessenger = new Messenger(new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message message)
            {
                handleClientMessage(message);
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        PendingIntent pendingIntent = PendingIntent.getActivity(
            App.getContext(), 0,
            new Intent(App.getContext(), MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT |
            PendingIntent.FLAG_IMMUTABLE);

        startForeground(App.NOTIFICATION_MAIN,
            new NotificationCompat.Builder(
                this, App.NOTIFICATION_CHANNEL_MAIN_ID)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle("Twilight Line")
            .setContentText("Twilight Line Proxy Started")
            .setContentIntent(pendingIntent)
            .build());

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return this.selfMessenger.getBinder();
    }

    private void handleClientMessage(Message message)
    {
        if (message.what == MESSAGE_START_PROXY_REQUEST) {
            onMessageStartProxyRequest(message);
        } else if (message.what == MESSAGE_STOP_PROXY_REQUEST) {
            onMessageStopProxyRequest(message);
            stopSelf();
        }
    }

    private void onMessageStartProxyRequest(Message request)
    {
        this.clientMessenger = request.replyTo;

        Bundle b = (Bundle)request.obj;

        boolean isGlobalProxy = b.getBoolean("is_global_proxy");
        String[] allowedAppList =
            TextUtils.split(b.getString("allowed_app_list"), "\\|");
        String proxyConfigName = b.getString("proxy_config_name");

        if (startVpnService(isGlobalProxy, allowedAppList) == false) {
            Log.e(App.TAG, "start vpn service failed");
            return;
        }
        if (startTwilightLineClient(proxyConfigName) == false) {
            Log.e(App.TAG, "start tlclient failed");
            return;
        }
        if (startTun2Socks() == false) {
            Log.e(App.TAG, "start tun2socks failed");
            return;
        }

        Message response = Message.obtain();
        response.what = TwilightLineVpnService.MESSAGE_START_PROXY_RESPONSE;
        try {
            this.clientMessenger.send(response);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    private void onMessageStopProxyRequest(Message request)
    {
        stopTun2Socks();
        stopTwilightLineClient();
        stopVpnService();

        Message response = Message.obtain();
        response.what = TwilightLineVpnService.MESSAGE_STOP_PROXY_RESPONSE;
        try {
            this.clientMessenger.send(response);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        this.clientMessenger = null;
    }

    private boolean startVpnService(boolean isGlobalProxy, String[] allowedAppList)
    {
        VpnService.Builder b = new VpnService.Builder();
        b.setMtu(VPN_MTU);
        b.setSession(App.NAME);
        // ipv4
        b.addAddress(VPN_TUN_DEVICE_IPV4, 24);
        b.addDnsServer("8.8.8.8");
        b.addRoute("0.0.0.0", 0);

        String selfApp = App.getContext().getPackageName();
        if (isGlobalProxy) {
            try {
                b.addDisallowedApplication(selfApp);
            } catch (Exception e) {
                Log.e(App.TAG, String.format(
                    "add disallowed app(%s) failed", selfApp));
            }
        } else {
            for (String allowedApp : allowedAppList) {
                if (allowedApp.equals(selfApp)) {
                    continue;
                }

                try {
                    b.addAllowedApplication(allowedApp);
                } catch (Exception e) {
                    Log.e(App.TAG, String.format(
                        "add allowed app(%s) failed", allowedApp));
                    return false;
                }
            }
        }

        try {
            this.vpnFileDescriptor = b.establish();
        } catch (Exception e) {
            Log.e(App.TAG, String.format(
                "establish vpn failed: %s", e.toString()));
            return false;
        }

        return true;
    }

    private void stopVpnService()
    {
        if (this.vpnFileDescriptor != null) {
            try {
                this.vpnFileDescriptor.close();
            } catch (Exception e) {
            }
            this.vpnFileDescriptor = null;
        }
    }

    private boolean startTwilightLineClient(String configName)
    {
        String progPath =
            App.getContext().getApplicationInfo().nativeLibraryDir +
            "/libtlclient.so";
        String configPath = App.getContext().getCacheDir() +
            "/tl-client-config.json";
        if (AppUtil.copyAsset(
                "config/tl-client/tlclient-" + configName + ".json",
                configPath) == false) {
            return false;
        }
        String cmd = progPath +
            " -e " + configPath +
            " -l 127.0.0.1:9051";
        Log.i(App.TAG, String.format("start %s", cmd));

        try {
            this.twilightLineClientProcess =
                Runtime.getRuntime().exec(cmd);
            AppUtil.logStream("tlclient",
                this.twilightLineClientProcess.getInputStream());
            AppUtil.logStream("tlclient",
                this.twilightLineClientProcess.getErrorStream());
        } catch (Exception e) {
            Log.e(App.TAG, String.format(
                "start failed: %s", e.toString()));
            return false;
        }

        return true;
    }

    private void stopTwilightLineClient()
    {
        if (this.twilightLineClientProcess != null) {
            try {
                this.twilightLineClientProcess.destroy();
            } catch (Exception e) {
            }
            this.twilightLineClientProcess = null;
        }
    }

    private boolean startTun2Socks()
    {
        engine.Key key = new engine.Key();
        key.setDevice("fd://" + vpnFileDescriptor.getFd());
        key.setProxy("socks5://127.0.0.1:9051");

        engine.Engine.insert(key);
        engine.Engine.start();
        // vpn fd owner transfer to tun2socks
        // prevent double free here
        this.vpnFileDescriptor.detachFd();
        this.vpnFileDescriptor = null;

        return true;
    }

    private void stopTun2Socks()
    {
        engine.Engine.stop();
    }
}
