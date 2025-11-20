package com.example.handgestureremote;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseDetectorOptionsBase;
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions;
import com.google.mlkit.vision.pose.PoseLandmark;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// ملاحظة: تم حذف إعدادات Pygame (الصوت) لأنها لا تعمل في نظام Android بالطريقة المباشرة.
// يجب استخدام Android AudioManager بدلاً منها، وهو موضوع منفصل.

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
    
    private ExecutorService cameraExecutor;
    private PreviewView viewFinder;
    private TextView statusTextView;
    private PoseDetector poseDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // تم حل خطأ 'R' عبر ملف activity_main.xml

        viewFinder = findViewById(R.id.viewFinder);
        statusTextView = findViewById(R.id.gesture_status);
        cameraExecutor = Executors.newSingleThreadExecutor();

        // 1. إعداد Pose Detector (باستخدام خيارات AccuratePoseDetectorOptions)
        PoseDetectorOptionsBase options = new AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                .build();
        poseDetector = PoseDetection.getClient(options);

        // 2. طلب صلاحيات الكاميرا
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindUseCases(cameraProvider);
            } catch (Exception e) {
                Log.e("GestureApp", "Camera initialization failed.", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT) // استخدام الكاميرا الأمامية
                .build();

        preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

        // 3. إعداد تحليل الصور (Image Analysis)
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            // تحويل الصورة إلى تنسيق ML Kit
            InputImage image = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees()
            );

            // تمرير الصورة إلى Pose Detector
            poseDetector.process(image)
                    .addOnSuccessListener(this::processPose)
                    .addOnFailureListener(e -> Log.e("GestureApp", "Pose detection failed.", e))
                    .addOnCompleteListener(task -> imageProxy.close()); // إغلاق الصورة بعد الانتهاء
        });

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }

    // 4. دالة معالجة الإيماءات (المنطق)
    private void processPose(Pose pose) {
        // يمكنك هنا الوصول إلى معالم اليد (Hand Landmarks) أو الجسم
        // للإيماءات، نستخدم اليد.
        
        PoseLandmark rightIndex = pose.getPoseLandmark(PoseLandmark.RIGHT_INDEX);
        PoseLandmark rightThumb = pose.getPoseLandmark(PoseLandmark.RIGHT_THUMB);

        if (rightIndex != null && rightThumb != null) {
            float distance = calculateDistance(rightIndex, rightThumb);

            // مثال: إيماءة "القرص" إذا كانت المسافة أقل من عتبة معينة
            if (distance < 50) { 
                updateStatus("✅ إيماءة القرص (Pinch) تم اكتشافها!");
            } else {
                updateStatus("Waiting for gesture...");
            }
        }
    }
    
    // دالة لحساب المسافة (بكسل غير دقيقة، لكنها مفيدة للمقارنة)
    private float calculateDistance(PoseLandmark p1, PoseLandmark p2) {
        float dx = p1.getPosition().x - p2.getPosition().x;
        float dy = p1.getPosition().y - p2.getPosition().y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void updateStatus(String status) {
        runOnUiThread(() -> statusTextView.setText(status));
    }

    // 5. التحقق من الصلاحيات ونتائجها
    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
