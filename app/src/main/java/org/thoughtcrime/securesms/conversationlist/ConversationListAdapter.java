package org.thoughtcrime.securesms.conversationlist;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.VideoController;
import com.google.android.gms.ads.formats.UnifiedNativeAd;
import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;

import org.jetbrains.annotations.NotNull;
import org.signal.paging.PagingController;
import org.thoughtcrime.securesms.BindableConversationListItem;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversationlist.model.Conversation;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.nativead.AdViewHolder;
import org.thoughtcrime.securesms.util.CachedInflater;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

class ConversationListAdapter extends ListAdapter<Object, RecyclerView.ViewHolder> {

  private static final int TYPE_AD      = 0;
  private static final int TYPE_THREAD      = 1;
  private static final int TYPE_ACTION      = 2;
  private static final int TYPE_PLACEHOLDER = 3;
  private static final int TYPE_HEADER      = 4;
  private static final int AD_OFFSET = 5;

  private enum Payload {
    TYPING_INDICATOR,
    SELECTION
  }

  private final GlideRequests               glideRequests;
  private final OnConversationClickListener onConversationClickListener;
  private final Map<Long, Conversation>     batchSet  = Collections.synchronizedMap(new LinkedHashMap<>());
  private       boolean                     batchMode = false;
  private final Set<Long>                   typingSet = new HashSet<>();

  private PagingController pagingController;
  private List<NativeAd> mNativeAds;

  protected ConversationListAdapter(@NonNull GlideRequests glideRequests,
                                    @NonNull OnConversationClickListener onConversationClickListener)
  {
    super(new ConversationDiffCallback());

    this.glideRequests               = glideRequests;
    this.onConversationClickListener = onConversationClickListener;

    this.setHasStableIds(true);
  }

  @Override
  public @NonNull RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    if (viewType == TYPE_AD) {
      View adItemLayoutView = LayoutInflater.from(parent.getContext()).inflate(R.layout.ad_unified_list, parent, false);
      return new AdViewHolder(adItemLayoutView);
    } else
    if (viewType == TYPE_ACTION) {
      ConversationViewHolder holder =  new ConversationViewHolder(LayoutInflater.from(parent.getContext())
                                                                                .inflate(R.layout.conversation_list_item_action, parent, false));

      holder.itemView.setOnClickListener(v -> {
        if (holder.getAdapterPosition() != RecyclerView.NO_POSITION) {
          onConversationClickListener.onShowArchiveClick();
        }
      });

      return holder;
    } else if (viewType == TYPE_THREAD) {
      ConversationViewHolder holder =  new ConversationViewHolder(CachedInflater.from(parent.getContext())
                                                                                .inflate(R.layout.conversation_list_item_view, parent, false));

      holder.itemView.setOnClickListener(v -> {
        int position = holder.getAdapterPosition();

        if (position != RecyclerView.NO_POSITION) {
          if (getItem(position) instanceof Conversation) {
            onConversationClickListener.onConversationClick((Conversation) getItem(position));
          }
        }
      });

      holder.itemView.setOnLongClickListener(v -> {
        int position = holder.getAdapterPosition();

        if (position != RecyclerView.NO_POSITION) {
          if (getItem(position) instanceof NativeAd) return false;
          return onConversationClickListener.onConversationLongClick((Conversation) getItem(position));
        }

        return false;
      });
      return holder;
    } else if (viewType == TYPE_PLACEHOLDER) {
      View v = new FrameLayout(parent.getContext());
      v.setLayoutParams(new FrameLayout.LayoutParams(1, ViewUtil.dpToPx(100)));
      return new PlaceholderViewHolder(v);
    } else if (viewType == TYPE_HEADER) {
      View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.conversation_list_item_header, parent, false);
      return new HeaderViewHolder(v);
    } else {
      throw new IllegalStateException("Unknown type! " + viewType);
    }
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
    if (payloads.isEmpty()) {
      onBindViewHolder(holder, position);
    } else if (holder instanceof ConversationViewHolder) {
      for (Object payloadObject : payloads) {
        if (payloadObject instanceof Payload) {
          Payload payload = (Payload) payloadObject;

          if (payload == Payload.SELECTION) {
            ((ConversationViewHolder) holder).getConversationListItem().setBatchMode(batchMode);
          } else {
            ((ConversationViewHolder) holder).getConversationListItem().updateTypingIndicator(typingSet);
          }
        }
      }
    }
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    if (holder.getItemViewType() == TYPE_AD) {
      NativeAd nativeAd = (NativeAd) getItem(position);
      populateUnifiedNativeAdView(nativeAd, ((AdViewHolder) holder).getAdView());

    } else
    if (holder.getItemViewType() == TYPE_ACTION || holder.getItemViewType() == TYPE_THREAD) {
      ConversationViewHolder casted       = (ConversationViewHolder) holder;
      Conversation           conversation = (Conversation) Objects.requireNonNull(getItem(position));

      casted.getConversationListItem().bind(conversation.getThreadRecord(),
                                            glideRequests,
                                            Locale.getDefault(),
                                            typingSet,
                                            getBatchSelectionIds(),
                                            batchMode);
    } else if (holder.getItemViewType() == TYPE_HEADER) {
      HeaderViewHolder casted       = (HeaderViewHolder) holder;
      Conversation     conversation = (Conversation) Objects.requireNonNull(getItem(position));
      switch (conversation.getType()) {
        case PINNED_HEADER:
          casted.headerText.setText(R.string.conversation_list__pinned);
          break;
        case UNPINNED_HEADER:
          casted.headerText.setText(R.string.conversation_list__chats);
          break;
        default:
          throw new IllegalArgumentException();
      }
    }
  }

  @Override
  public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
    if (holder instanceof ConversationViewHolder) {
      ((ConversationViewHolder) holder).getConversationListItem().unbind();
    }
  }

  @Override
  protected Object getItem(int position) {
    if (pagingController != null) {
      pagingController.onDataNeededAroundIndex(position);
    }

    return super.getItem(position);
  }

  @Override
  public long getItemId(int position) {

    if (getItem(position) instanceof Conversation) {
      Conversation item = (Conversation) getItem(position);

      if (item == null) {
        return 0;
      }

      switch (item.getType()) {
        case THREAD:          return item.getThreadRecord().getThreadId();
        case PINNED_HEADER:   return -1;
        case UNPINNED_HEADER: return -2;
        case ARCHIVED_FOOTER: return -3;
        default:              throw new AssertionError();
      }
    } else {
      return TYPE_AD;
    }

  }

  public void setPagingController(@Nullable PagingController pagingController) {
    this.pagingController = pagingController;
  }

  void setTypingThreads(@NonNull Set<Long> typingThreadSet) {
    this.typingSet.clear();
    this.typingSet.addAll(typingThreadSet);

    notifyItemRangeChanged(0, getItemCount(), Payload.TYPING_INDICATOR);
  }

  void toggleConversationInBatchSet(@NonNull Conversation conversation) {
    if (batchSet.containsKey(conversation.getThreadRecord().getThreadId())) {
      batchSet.remove(conversation.getThreadRecord().getThreadId());
    } else if (conversation.getThreadRecord().getThreadId() != -1) {
      batchSet.put(conversation.getThreadRecord().getThreadId(), conversation);
    }

    notifyItemRangeChanged(0, getItemCount(), Payload.SELECTION);
  }

  Collection<Conversation> getBatchSelection() {
    return batchSet.values();
  }

  @Override
  public int getItemViewType(int position) {
    Object item = getItem(position);
    if (item instanceof NativeAd) {
      return TYPE_AD;
    } else if (item instanceof Conversation) {
      Conversation conversation = (Conversation) getItem(position);

      if (conversation == null) {
        return TYPE_PLACEHOLDER;
      }

      switch (conversation.getType()) {
        case PINNED_HEADER:
        case UNPINNED_HEADER:
          return TYPE_HEADER;
        case ARCHIVED_FOOTER:
          return TYPE_ACTION;
        case THREAD:
          return TYPE_THREAD;
        default:
          throw new IllegalArgumentException();
      }
    }

    return TYPE_PLACEHOLDER;
  }

  /*
   * AdMob code
   */
  public void addNativeAd(List<NativeAd> mNativeAds) {
    this.mNativeAds = mNativeAds;

    List<Object> clist = getCurrentList();
    //List<Object> list = Collections.unmodifiableList(new ArrayList<>(clist));
    List<Object> list = new ArrayList<>();
    list.addAll(clist);

    int index = AD_OFFSET;
    for (NativeAd ad : mNativeAds) {
      if (index >= getItemCount()) {
        break;
      }

      list.add(index, ad);

      index = index + AD_OFFSET;
    }

    submitList(list);

    notifyDataSetChanged();
  }

  private void populateUnifiedNativeAdView(NativeAd nativeAd, NativeAdView adView) {
    // Set the media view.
//    adView.setMediaView((MediaView) adView.findViewById(R.id.ad_media));

    // Set other ad assets.
    adView.setHeadlineView(adView.findViewById(R.id.ad_headline));
    adView.setBodyView(adView.findViewById(R.id.ad_body));
    adView.setCallToActionView(adView.findViewById(R.id.ad_call_to_action));
    adView.setIconView(adView.findViewById(R.id.ad_app_icon));
    /*adView.setPriceView(adView.findViewById(R.id.ad_price));*/
    adView.setStarRatingView(adView.findViewById(R.id.ad_stars));
    /*adView.setStoreView(adView.findViewById(R.id.ad_store));*/
    adView.setAdvertiserView(adView.findViewById(R.id.ad_advertiser));

//    adView.getMediaView().setMediaContent(nativeAd.getMediaContent());

    // The headline and mediaContent are guaranteed to be in every UnifiedNativeAd.
    ((TextView) adView.getHeadlineView()).setText(nativeAd.getHeadline());

    // These assets aren't guaranteed to be in every UnifiedNativeAd, so it's important to
    // check before trying to display them.
    if (nativeAd.getBody() == null) {
      adView.getBodyView().setVisibility(View.INVISIBLE);
    } else {
      adView.getBodyView().setVisibility(View.VISIBLE);
      ((TextView) adView.getBodyView()).setText(nativeAd.getBody());
    }

    if (nativeAd.getCallToAction() == null) {
      adView.getCallToActionView().setVisibility(View.INVISIBLE);
    } else {
      adView.getCallToActionView().setVisibility(View.VISIBLE);
      ((Button) adView.getCallToActionView()).setText(nativeAd.getCallToAction());
    }

    if (nativeAd.getIcon() == null) {
      adView.getIconView().setVisibility(View.GONE);
    } else {
      ((ImageView) adView.getIconView()).setImageDrawable(
          nativeAd.getIcon().getDrawable());
      adView.getIconView().setVisibility(View.VISIBLE);
    }

    /*if (nativeAd.getPrice() == null) {
      adView.getPriceView().setVisibility(View.INVISIBLE);
    } else {
      adView.getPriceView().setVisibility(View.VISIBLE);
      ((TextView) adView.getPriceView()).setText(nativeAd.getPrice());
    }

    if (nativeAd.getStore() == null) {
      adView.getStoreView().setVisibility(View.INVISIBLE);
    } else {
      adView.getStoreView().setVisibility(View.VISIBLE);
      ((TextView) adView.getStoreView()).setText(nativeAd.getStore());
    }*/

    if (nativeAd.getStarRating() == null) {
      adView.getStarRatingView().setVisibility(View.INVISIBLE);
    } else {
      ((RatingBar) adView.getStarRatingView())
          .setRating(nativeAd.getStarRating().floatValue());
      adView.getStarRatingView().setVisibility(View.VISIBLE);
    }

    if (nativeAd.getAdvertiser() == null) {
      adView.getAdvertiserView().setVisibility(View.INVISIBLE);
    } else {
      ((TextView) adView.getAdvertiserView()).setText(nativeAd.getAdvertiser());
      adView.getAdvertiserView().setVisibility(View.VISIBLE);
    }

    // This method tells the Google Mobile Ads SDK that you have finished populating your
    // native ad view with this native ad.
    adView.setNativeAd(nativeAd);

    // Get the video controller for the ad. One will always be provided, even if the ad doesn't
    // have a video asset.
//    VideoController vc = nativeAd.getVideoController();
//
//    // Updates the UI to say whether or not this ad has a video asset.
//    if (vc.hasVideoContent()) {
//      videoStatus.setText(String.format(Locale.getDefault(),
//                                        "Video status: Ad contains a %.2f:1 video asset.",
//                                        vc.getAspectRatio()));
//
//      // Create a new VideoLifecycleCallbacks object and pass it to the VideoController. The
//      // VideoController will call methods on this object when events occur in the video
//      // lifecycle.
//      vc.setVideoLifecycleCallbacks(new VideoController.VideoLifecycleCallbacks() {
//        @Override
//        public void onVideoEnd() {
//          // Publishers should allow native ads to complete video playback before
//          // refreshing or replacing them with another ad in the same UI location.
//          refresh.setEnabled(true);
//          videoStatus.setText("Video status: Video playback has ended.");
//          super.onVideoEnd();
//        }
//      });
//    } else {
//      videoStatus.setText("Video status: Ad does not contain a video asset.");
//      refresh.setEnabled(true);
//    }
  }


  @NonNull Set<Long> getBatchSelectionIds() {
    return batchSet.keySet();
  }

  void selectAllThreads() {
    for (int i = 0; i < super.getItemCount(); i++) {
      if (getItem(i) instanceof Conversation) {
        Conversation conversation = (Conversation) getItem(i);
        if (conversation != null && conversation.getThreadRecord().getThreadId() >= 0) {
          batchSet.put(conversation.getThreadRecord().getThreadId(), conversation);
        }
      }
    }

    notifyItemRangeChanged(0, getItemCount(), Payload.SELECTION);
  }

  void initializeBatchMode(boolean toggle) {
    this.batchMode = toggle;
    unselectAllThreads();
  }

  private void unselectAllThreads() {
    batchSet.clear();

    notifyItemRangeChanged(0, getItemCount(), Payload.SELECTION);
  }

  static final class ConversationViewHolder extends RecyclerView.ViewHolder {

    private final BindableConversationListItem conversationListItem;

    ConversationViewHolder(@NonNull View itemView) {
      super(itemView);

      conversationListItem = (BindableConversationListItem) itemView;
    }

    public BindableConversationListItem getConversationListItem() {
      return conversationListItem;
    }
  }

  private static final class ConversationDiffCallback extends DiffUtil.ItemCallback<Object> {

//    @Override
//    public boolean areItemsTheSame(@NonNull Conversation oldItem, @NonNull Conversation newItem) {
//      return oldItem.getThreadRecord().getThreadId() == newItem.getThreadRecord().getThreadId();
//    }
//
//    @Override
//    public boolean areContentsTheSame(@NonNull Conversation oldItem, @NonNull Conversation newItem) {
//      return oldItem.equals(newItem);
//    }

    @Override public boolean areItemsTheSame(@NonNull @NotNull Object oldItem, @NonNull @NotNull Object newItem) {
      if (newItem instanceof NativeAd) return false;
      if (oldItem instanceof NativeAd) return false;
      return ((Conversation)oldItem).getThreadRecord().getThreadId() == ((Conversation)newItem).getThreadRecord().getThreadId();
    }

    @Override public boolean areContentsTheSame(@NonNull @NotNull Object oldItem, @NonNull @NotNull Object newItem) {
      if (newItem instanceof NativeAd) return false;
      if (oldItem instanceof NativeAd) return false;
      return ((Conversation)oldItem).equals(newItem);
    }
  }

  private static class PlaceholderViewHolder extends RecyclerView.ViewHolder {
    PlaceholderViewHolder(@NonNull View itemView) {
      super(itemView);
    }
  }

  static class HeaderViewHolder extends RecyclerView.ViewHolder {
    private TextView headerText;

    public HeaderViewHolder(@NonNull View itemView) {
      super(itemView);
      headerText = (TextView) itemView;
    }
  }

  interface OnConversationClickListener {
    void onConversationClick(Conversation conversation);
    boolean onConversationLongClick(Conversation conversation);
    void onShowArchiveClick();
  }
}
