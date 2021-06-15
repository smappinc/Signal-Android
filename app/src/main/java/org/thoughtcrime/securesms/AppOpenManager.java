package org.thoughtcrime.securesms;

import static androidx.lifecycle.Lifecycle.Event.ON_START;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.appopen.AppOpenAd;

import org.signal.core.util.logging.Log;

import java.util.Date;

/** Prefetches App Open Ads. */
public class AppOpenManager implements LifecycleObserver,Application.ActivityLifecycleCallbacks {

  private Activity currentActivity;
  private static final String LOG_TAG = "AppOpenManager";
  private static final String AD_UNIT_ID = "ca-app-pub-6991932537186982/1253301255";
  private AppOpenAd appOpenAd = null;
  private long loadTime = 0;

  private AppOpenAd.AppOpenAdLoadCallback loadCallback;
  private static boolean isShowingAd = false;

  private final ApplicationContext myApplication;

  /** Constructor */
  public AppOpenManager(ApplicationContext myApplication) {
    this.myApplication = myApplication;
    this.myApplication.registerActivityLifecycleCallbacks(this);
    ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
  }

  /** LifecycleObserver methods */
  @OnLifecycleEvent(ON_START)
  public void onStart() {
    showAdIfAvailable();
    Log.d(LOG_TAG, "onStart");
  }

  /** Shows the ad if one isn't already showing. */
  public void showAdIfAvailable() {
    // Only show ad if there is not already an app open ad currently showing
    // and an ad is available.
    if(Looper.myLooper() != Looper.getMainLooper() && currentActivity!=null) {
      currentActivity.runOnUiThread(() -> {
        if (!isShowingAd && isAdAvailable()) {
          FullScreenContentCallback fullScreenContentCallback =
              new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                  // Set the reference to null so isAdAvailable() returns false.
                  AppOpenManager.this.appOpenAd = null;
                  isShowingAd                   = false;
                  fetchAd();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(AdError adError) {}

                @Override
                public void onAdShowedFullScreenContent() {
                  isShowingAd = true;
                }
              };

          appOpenAd.setFullScreenContentCallback(fullScreenContentCallback);
          appOpenAd.show(currentActivity);

        } else {
          fetchAd();
        }
      });
    } else {
      if (!isShowingAd && isAdAvailable()) {
        FullScreenContentCallback fullScreenContentCallback =
            new FullScreenContentCallback() {
              @Override
              public void onAdDismissedFullScreenContent() {
                // Set the reference to null so isAdAvailable() returns false.
                AppOpenManager.this.appOpenAd = null;
                isShowingAd                   = false;
                fetchAd();
              }

              @Override
              public void onAdFailedToShowFullScreenContent(AdError adError) {}

              @Override
              public void onAdShowedFullScreenContent() {
                isShowingAd = true;
              }
            };

        appOpenAd.setFullScreenContentCallback(fullScreenContentCallback);
        appOpenAd.show(currentActivity);

      } else {
        fetchAd();
      }
    }
  }

  /** Request an ad */
  public void fetchAd() {
    // Have unused ad, no need to fetch another.
    if (isAdAvailable()) {
      return;
    }

    loadCallback =
        new AppOpenAd.AppOpenAdLoadCallback() {
          /**
           * Called when an app open ad has loaded.
           *
           * @param ad the loaded app open ad.
           */
          @Override
          public void onAdLoaded(AppOpenAd ad) {
            AppOpenManager.this.appOpenAd = ad;
            AppOpenManager.this.loadTime = (new Date()).getTime();
            Log.d(LOG_TAG, "App Open Ad loaded");
          }

          /**
           * Called when an app open ad has failed to load.
           *
           * @param loadAdError the error.
           */
          @Override
          public void onAdFailedToLoad(LoadAdError loadAdError) {
            // Handle the error.
            Log.d(LOG_TAG, "App Open Ad failed to load");
          }

        };
    AdRequest request = getAdRequest();
    AppOpenAd.load(
        myApplication, AD_UNIT_ID, request,
        AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT, loadCallback);
  }

  private boolean wasLoadTimeLessThanNHoursAgo(long numHours) {
    long dateDifference = (new Date()).getTime() - this.loadTime;
    long numMilliSecondsPerHour = 3600000;
    return (dateDifference < (numMilliSecondsPerHour * numHours));
  }

  /** Creates and returns ad request. */
  private AdRequest getAdRequest() {
    return new AdRequest.Builder().build();
  }

  /** Utility method that checks if ad exists and can be shown. */
  public boolean isAdAvailable() {
    return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4);
  }

  /** ActivityLifecycleCallback methods */
  @Override
  public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

  @Override
  public void onActivityStarted(Activity activity) {
    currentActivity = activity;
  }

  @Override
  public void onActivityResumed(Activity activity) {
    currentActivity = activity;
  }

  @Override
  public void onActivityStopped(Activity activity) {}

  @Override
  public void onActivityPaused(Activity activity) {}

  @Override
  public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {}

  @Override
  public void onActivityDestroyed(Activity activity) {
    currentActivity = null;
  }
}
