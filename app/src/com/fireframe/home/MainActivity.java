package com.fireframe.home;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewTreeObserver;

// FireFrame as an app: the Home screen in a window of its own. See HomeScreen for everything else.
public class MainActivity extends Activity {
    HomeScreen screen;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        screen = new HomeScreen(this);
        screen.applyExtras(getIntent());
        setContentView(screen);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        screen.applyExtras(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        HomeService.pausedUntil = 0;
        HomeWatcher.start(this);
        screen.onShown(false);
        // Once this has drawn, the overlay covering Amazon's launcher can step aside. Not sooner, or
        // Amazon's launcher shows through for a moment.
        screen.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                screen.getViewTreeObserver().removeOnPreDrawListener(this);
                screen.postDelayed(HomeService::appShown, 150);
                return true;
            }
        });
    }

    // Fire TV starts on Amazon's home screen; this opens FireFrame as soon as the TV has booted, or FireFrame has updated.
    public static class Boot extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            HomeWatcher.start(c);
            c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }
}
