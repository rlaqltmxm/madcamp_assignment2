package io.madcamp.jh.madcamp_assignment2;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.media.ExifInterface;
import android.support.v4.app.Fragment;
import android.support.v4.content.FileProvider;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.facebook.AccessToken;
import com.facebook.Profile;
import com.facebook.ProfileManager;
import com.google.android.gms.common.internal.safeparcel.SafeParcelable;
import com.google.android.gms.maps.model.LatLng;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public class Tab2Fragment extends Fragment {
    public static final String ARG_PAGE = "ARG_PAGE";
    private int mPage;
    private Context context;
    private View top;

    private boolean isFabOpen;

    private static final int REQ_IMG_FILE = 1;
    private static final int REQ_TAKE_PHOTO = 2;

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private ArrayList<Image> imageList;
    public Tab2Adapter adapter;

    private Uri tempPhotoUri;

    /* TabPagerAdapter에서 Fragment 생성할 때 사용하는 메소드 */
    public static Tab2Fragment newInstance(int page, ArrayList<Image> imageList) {
        Bundle args = new Bundle();
        args.putInt(ARG_PAGE, page);
        Tab2Fragment fragment = new Tab2Fragment();
        fragment.setArguments(args);
        fragment.imageList = imageList;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPage = getArguments().getInt(ARG_PAGE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        top = inflater.inflate(R.layout.fragment_tab2, container, false);
        this.context = getActivity();

        initializeFloatingActionButton();
        initializeRecyclerView();

        swipeRefreshLayout = top.findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refresh();
            }
        });

        refresh();

        return top;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    public void initializeFloatingActionButton() {
        final FloatingActionButton[] fab = new FloatingActionButton[] {
                top.findViewById(R.id.fab),
                top.findViewById(R.id.fab1),
                top.findViewById(R.id.fab2),
        };

        isFabOpen = false;

        View.OnClickListener onClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = v.getId();
                switch(id) {
                    case R.id.fab:
                        anim();
                        break;
                    case R.id.fab1:
                        addImageFromFile();
                        break;
                    case R.id.fab2:
                        addImageFromCamera();
                        break;
                }
            }

            public void anim() {
                if(isFabOpen) {
                    for(int i = 1; i < fab.length; i++) {
                        Animation a = AnimationUtils.loadAnimation(context, R.anim.fab_close);
                        a.setStartOffset((fab.length - i - 1) * 50);
                        fab[i].startAnimation(a);
                        fab[i].setClickable(false);
                    }
                    isFabOpen = false;
                } else {
                    for(int i = 1; i < fab.length; i++) {
                        Animation a = AnimationUtils.loadAnimation(context, R.anim.fab_open);
                        a.setStartOffset((i - 1) * 50);
                        fab[i].startAnimation(a);
                        fab[i].setClickable(true);
                    }
                    isFabOpen = true;
                }
            }
        };

        for(int i = 0; i < fab.length; i++) {
            fab[i].setOnClickListener(onClickListener);
        }
    }

    private String packJSON() {
        try {
            JSONArray arr = new JSONArray();
            ArrayList<Image> list = adapter.dataSet;
            if(list != null) {
                for (Image p : list) {
                    JSONObject item = p.toJSONObject(false);
                    if(item == null) {
                        throw new JSONException("Malformed Image");
                    }
                    arr.put(item);
                }
            }
            return arr.toString();
        } catch(JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void unpackJSON(String src, ArrayList<Image> list) {
        try {
            JSONArray arr = new JSONArray(src);
            for(int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                Image image = Image.fromJSON(arr.getJSONObject(i));
                String newUri = getString(R.string.server_url) + "static/" + image._id + ".jpg";
                image.uri = Uri.parse(newUri);
                image.updateTag();
                list.add(image);
            }
        } catch(JSONException e) {
            Log.d("Exception", "JSON Parsing Failed");
        }
    }

    private void loadFromJSON(String src) {
        imageList.clear();
        unpackJSON(src, imageList);
        Collections.sort(imageList, new Comparator<Image>() {
            @Override
            public int compare(Image o1, Image o2) {
                return Long.valueOf(o2.date).compareTo(o1.date);
            }
        });
        adapter.notifyDataSetChanged();
    }


    public void initializeRecyclerView() {
        recyclerView = top.findViewById(R.id.recycler_view);
        // adapter = new Tab2Adapter(loadImageListFromInternal());
        adapter = new Tab2Adapter(imageList);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new GridLayoutManager(context, 3));
        recyclerView.setAdapter(adapter);

        recyclerView.addOnItemTouchListener(new RecyclerItemClickListener(
                context, recyclerView, new RecyclerItemClickListener.OnItemClickListener() {
            @Override
            public void onItemClick(View view, final int position) {
                final Dialog dialog = new Dialog(getActivity(), android.R.style.Theme_Black_NoTitleBar);
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.argb(100, 0, 0, 0)));
                dialog.setContentView(R.layout.dialog_image);

                final Image image = adapter.get(position);
                Glide.with(getActivity())
                        .load(image.uri.toString())
                        .thumbnail(0.1f)
                        .apply(new RequestOptions().override(Target.SIZE_ORIGINAL))
                        .into((ImageView)dialog.findViewById(R.id.image_view));
                Log.d("Test@uri", image.uri.toString());

                dialog.findViewById(R.id.blank).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                });

                final TextView deleteTextView = dialog.findViewById(R.id.text_view_delete);

                if(imageList.get(position).fb_id.equals(AccessToken.getCurrentAccessToken().getUserId())) {
                    deleteTextView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                            builder.setMessage("정말로 이미지를 삭제하시겠습니까?")
                                    .setPositiveButton("예", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int which) {
                                    httpDeleteWithId(imageList.get(position));
                                    d.dismiss();
                                    dialog.dismiss();
                                }
                                    }).setNegativeButton("아니오", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int which) {
                                    dialog.dismiss();
                                    d.dismiss();
                                }
                            });
                            builder.create().show();
                        }
                    });
                } else {
                    dialog.findViewById(R.id.text_view_delete).setVisibility(View.INVISIBLE);
                }

                final TextView likeTextView = dialog.findViewById(R.id.text_view_like);

                likeTextView.setText(image.getLikeAsString());
                likeTextView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        httpPutWithId(imageList.get(position));
                        likeTextView.setText(image.getLikeAsString());
                    }
                });

                final TextView tagTextView = dialog.findViewById(R.id.text_view_tag);
                tagTextView.setText(image.tag);

                dialog.getWindow().getAttributes().windowAnimations = R.style.ImageDialogAnimation;

                dialog.setCancelable(true);
                dialog.show();
            }

            @Override
            public void onLongItemClick(View view, int position) {
                /* DO NOTHING */
            }
        }));
    }

    public void addImageFromFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        if(Build.VERSION.SDK_INT >= 18)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setType("image/*");
        startActivityForResult(intent, REQ_IMG_FILE);
    }

    public void addImageFromCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if(intent.resolveActivity(context.getPackageManager()) != null) {
            File photoFile = null;
            try {
                String imageFileName = getTime();
                File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                photoFile = File.createTempFile(imageFileName, ".jpg", storageDir);
            } catch(IOException e) {
                e.printStackTrace();
            }
            if(photoFile != null) {
                tempPhotoUri = FileProvider.getUriForFile(context, "io.madcamp.jh.madcamp_assignment2.fileprovider", photoFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, tempPhotoUri);
                startActivityForResult(intent, REQ_TAKE_PHOTO);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode == Activity.RESULT_OK && (requestCode == REQ_IMG_FILE || requestCode == REQ_TAKE_PHOTO)) {
            switch(requestCode) {
                case REQ_IMG_FILE:
                    if(data.getData() != null) {
                        Log.d("Test@onRes", "Single");
                        addImage(data.getData());
                    } else if(Build.VERSION.SDK_INT >= 18) {
                        if (data.getClipData() != null) {
                            Log.d("Test@onRes", "Multiple");
                            ClipData clipData = data.getClipData();
                            for (int i = 0; i < clipData.getItemCount(); i++) {
                                addImage(clipData.getItemAt(i).getUri());
                            }
                        }
                    }
                    break;
                case REQ_TAKE_PHOTO:
                    addImage(tempPhotoUri);
                    break;
            }
        }
    }

    private void addImage(Uri uri) {
        sendImage(uri);
    }


    private Image sendImage(Uri uri) {
        Image image = new Image();
        image.name = Profile.getCurrentProfile().getName();
        image.date = (new Date()).getTime();
        // String imageFileName = getTime() + ".jpg";
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri);
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            Matrix matrix = new Matrix();

            InputStream is = context.getContentResolver().openInputStream(uri);
            if(is != null) {
                ExifInterface ei = new ExifInterface(is);
                int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_UNDEFINED);
                float angle = 0.0f;
                switch(orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90: angle = 90.f; break;
                    case ExifInterface.ORIENTATION_ROTATE_180: angle = 180.f; break;
                    case ExifInterface.ORIENTATION_ROTATE_270: angle = 270.f; break;
                }
                matrix.postRotate(angle);

                double[] ll = ei.getLatLong();
                if(ll != null) {
                    LatLng latLng = new LatLng(ll[0], ll[1]);
                    image.latLng = latLng;
                    Log.d("Test@LL", "lat: " + latLng.latitude + ", lng: " + latLng.longitude);
                } else {
                    Log.d("Test@LL", "NOTHING");
                }
            }

            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            rotated.compress(Bitmap.CompressFormat.JPEG, 95, baos);
            byte[] byteArray = baos.toByteArray();
            String encoded = Base64.encodeToString(byteArray, Base64.DEFAULT);

            image.uri = null;
            httpPostWithId(image, encoded);

            return image;
        } catch(IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String getTime() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    }



    private void refresh() {
        httpGet();
        swipeRefreshLayout.setRefreshing(false);
    }


    /* Networking */
    private void httpError() {
        Log.d("Test@Retrofit", "Failed");
        Toast.makeText(getActivity(), "Failed to load", Toast.LENGTH_SHORT).show();
        swipeRefreshLayout.setRefreshing(false);
    }


    public interface HttpGetService {
        @GET("api/photo")
        Call<ResponseBody> getUserRepositories();
    }

    private void httpGet() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(context.getString(R.string.server_url))
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        Log.d("Test@POST", "Built");

        HttpGetService service = retrofit.create(HttpGetService.class);

        RequestBody params = RequestBody.create(okhttp3.MediaType.parse("application/json; charset=utf-8"),
                "aaa");

        Call<ResponseBody> request = service.getUserRepositories();
        request.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                Log.d("Test@Retrofit", "Responsed");
                try {
                    String s = response.body().string();
                    Log.d("Test@Retrofit", s);
                    loadFromJSON(s);
                } catch(Exception e) { e.printStackTrace(); }
                Toast.makeText(getActivity(), "Done", Toast.LENGTH_SHORT).show();
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                httpError();
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    public interface HttpPostWithIdService {
        @POST("api/photo/{fb_id}")
        Call<ResponseBody> getUserRepositories(@Path("fb_id") String fb_id, @Body RequestBody params);
    }

    private void httpPostWithId(final Image image, final String encoded) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(context.getString(R.string.server_url))
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        Log.d("Test@POST", "Built");

        HttpPostWithIdService service = retrofit.create(HttpPostWithIdService.class);
        String userId = AccessToken.getCurrentAccessToken().getUserId();

        image.fb_id = userId;
        JSONObject o = image.toJSONObject(false);
        try {
            o.put("encoded", encoded);
        } catch(JSONException e) { return; }

        RequestBody params = RequestBody.create(okhttp3.MediaType.parse("application/json; charset=utf-8"),
                o.toString());

        Call<ResponseBody> request = service.getUserRepositories(userId, params);
        request.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                Log.d("Test@Retrofit", "Responsed");
                try {
                    String s = response.body().string();
                    Log.d("Test@Retrofit", s);
                    Image newImage;
                    try {
                        newImage = Image.fromJSON(new JSONObject(s));
                    } catch(JSONException e) {
                        Log.d("Test@JSON", "Failed");
                        return;
                    }
                    /* DO SOMETHING */
                    String newUri = getString(R.string.server_url) + "static/" + newImage._id + ".jpg";
                    Log.d("Test@newUri", newUri);
                    newImage.uri = Uri.parse(newUri);
                    adapter.add(newImage);
                    adapter.notifyDataSetChanged();
                } catch(Exception e) { e.printStackTrace(); }
                Toast.makeText(getActivity(), "Done", Toast.LENGTH_SHORT).show();
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                httpError();
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    public interface HttpPutWithIdService {
        @PUT("api/photo/{fb_id}/{id}")
        Call<ResponseBody> getUserRepositories(@Path("fb_id") String fb_id, @Path("id") String id);
    }

    private void httpPutWithId(final Image image) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(context.getString(R.string.server_url))
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        Log.d("Test@PUT", "Built");

        HttpPutWithIdService service = retrofit.create(HttpPutWithIdService.class);
        String userId = AccessToken.getCurrentAccessToken().getUserId();

        Call<ResponseBody> request = service.getUserRepositories(userId, image._id);
        request.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                Log.d("Test@Retrofit", "Responsed");
                try {
                    /* DO SOMETHING */
                    String s = response.body().string();
                    Log.d("Test@Retrofit", s);
                    //image.loadFromJSON(new JSONObject(s));
                    image.like += 1;
                } catch(Exception e) { e.printStackTrace(); }
                Toast.makeText(getActivity(), "Done", Toast.LENGTH_SHORT).show();
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                httpError();
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    public interface HttpDeleteWithIdService {
        @DELETE("api/photo/{fb_id}/{id}")
        Call<ResponseBody> getUserRepositories(@Path("fb_id") String fb_id, @Path("id") String id);
    }

    private void httpDeleteWithId(final Image image) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(context.getString(R.string.server_url))
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        Log.d("Test@DELETE", "Built");

        HttpDeleteWithIdService service = retrofit.create(HttpDeleteWithIdService.class);
        String userId = AccessToken.getCurrentAccessToken().getUserId();

        Call<ResponseBody> request = service.getUserRepositories(userId, image._id);
        request.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                Log.d("Test@Retrofit", "Responsed");
                try {
                    String s = response.body().string();
                    Log.d("Test@Retrofit", s);
                    /* DO SOMETHING */
                    adapter.remove(imageList.indexOf(image));
                    adapter.notifyDataSetChanged();
                } catch(Exception e) { e.printStackTrace(); }
                Toast.makeText(getActivity(), "Done", Toast.LENGTH_SHORT).show();
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                httpError();
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }
}
