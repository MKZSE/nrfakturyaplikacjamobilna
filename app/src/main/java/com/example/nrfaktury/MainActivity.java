package com.example.nrfaktury;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;
import android.graphics.Matrix;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private ImageView imgPreview;
    private ListView lstResults;
    private List<String> ocrResults = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private Bitmap capturedImage;

    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imgPreview = findViewById(R.id.imgPreview);
        lstResults = findViewById(R.id.lstResults);
        Button btnOpenCamera = findViewById(R.id.btnOpenCamera);
        Button btnSelectFromGallery = findViewById(R.id.btnSelectFromGallery);
        Button btnStartOcr = findViewById(R.id.btnStartOcr);
        Button btnRotateImage = findViewById(R.id.btnRotateImage);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, ocrResults);
        lstResults.setAdapter(adapter);

        btnRotateImage.setOnClickListener(view -> {
            if (capturedImage != null) {
                capturedImage = rotateBitmap(capturedImage, 90);
                imgPreview.setImageBitmap(capturedImage);
            } else {
                Toast.makeText(this, "Najpierw wykonaj zdjęcie lub wybierz obraz!", Toast.LENGTH_SHORT).show();
            }
        });

        lstResults.setOnItemLongClickListener((parent, view, position, id) -> {
            String selectedText = ocrResults.get(position);
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Skopiowany tekst", selectedText);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Skopiowano: " + selectedText, Toast.LENGTH_SHORT).show();
            return true;
        });

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Bundle extras = result.getData().getExtras();
                        if (extras != null) {
                            capturedImage = (Bitmap) extras.get("data");
                            imgPreview.setImageBitmap(capturedImage);
                        }
                    }
                });

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        try {
                            Uri imageUri = result.getData().getData();
                            capturedImage = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                            imgPreview.setImageBitmap(capturedImage);
                        } catch (IOException e) {
                            ocrResults.add("Błąd wczytywania obrazu: " + e.getMessage());
                            adapter.notifyDataSetChanged();
                        }
                    }
                });

        btnOpenCamera.setOnClickListener(view -> {
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraLauncher.launch(cameraIntent);
        });

        btnSelectFromGallery.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            galleryLauncher.launch(intent);
        });

        btnStartOcr.setOnClickListener(view -> {
            if (capturedImage != null) {
                processImageWithMlKit(capturedImage);
            } else {
                ocrResults.add("Najpierw wykonaj zdjęcie lub wybierz obraz!");
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void processImageWithMlKit(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(image)
                .addOnSuccessListener(resultText -> {
                    String recognizedText = resultText.getText().toUpperCase();

                    recognizedText = recognizedText.replaceAll("WYSTNSENIA", "WYSTAWIENIA");
                    ocrResults.clear();


                    Pattern invoicePattern = Pattern.compile("[^\\s/]+(?:/[^\\s/]+){2,}");
                    Matcher invoiceMatcher = invoicePattern.matcher(recognizedText);
                    if (invoiceMatcher.find()) {
                        String invoiceNumber = invoiceMatcher.group();
                        ocrResults.add("Numer faktury: " + invoiceNumber);
                    } else {
                        ocrResults.add("Nie znaleziono numeru faktury.");
                    }


                    String selectedDate = null;

                    Pattern exactLabelPattern = Pattern.compile("DATA WYSTAWIENIA", Pattern.CASE_INSENSITIVE);
                    Matcher exactLabelMatcher = exactLabelPattern.matcher(recognizedText);
                    if (exactLabelMatcher.find()) {
                        int labelEnd = exactLabelMatcher.end();
                        int endIndex = Math.min(recognizedText.length(), labelEnd + 50);
                        String afterLabel = recognizedText.substring(labelEnd, endIndex);
                        Pattern datePattern = Pattern.compile("\\b(\\d{4}[-.]\\d{2}[-.]\\d{2}|\\d{2}[-.]\\d{2}[-.]\\d{4})\\b");
                        Matcher dateAfterLabelMatcher = datePattern.matcher(afterLabel);
                        if (dateAfterLabelMatcher.find()) {
                            selectedDate = dateAfterLabelMatcher.group();
                        }
                    }


                    if (selectedDate == null) {

                        Pattern datePattern = Pattern.compile("\\b(\\d{4}[-.]\\d{2}[-.]\\d{2}|\\d{2}[-.]\\d{2}[-.]\\d{4})\\b");
                        Matcher dateMatcher = datePattern.matcher(recognizedText);
                        List<Integer> datePositions = new ArrayList<>();
                        List<String> foundDates = new ArrayList<>();
                        while (dateMatcher.find()) {
                            datePositions.add(dateMatcher.start());
                            foundDates.add(dateMatcher.group());
                        }

                        Pattern wordPattern = Pattern.compile("\\S+");
                        Matcher wordMatcher = wordPattern.matcher(recognizedText);
                        List<Integer> labelPositions = new ArrayList<>();
                        String[] targetLabels = {"WYSTAWIENIA", "WYSTAWIONO", "DNIA"};
                        while (wordMatcher.find()) {
                            String word = wordMatcher.group();
                            for (String target : targetLabels) {
                                if (isSimilar(word, target)) {
                                    labelPositions.add(wordMatcher.start());
                                    break;
                                }
                            }
                        }
                        int minDistance = Integer.MAX_VALUE;
                        for (int labelPos : labelPositions) {
                            for (int i = 0; i < datePositions.size(); i++) {
                                int distance = Math.abs(labelPos - datePositions.get(i));
                                if (distance < minDistance) {
                                    minDistance = distance;
                                    selectedDate = foundDates.get(i);
                                }
                            }
                        }

                        if (selectedDate == null && !foundDates.isEmpty()) {
                            selectedDate = foundDates.get(0);
                        }
                    }

                    if (selectedDate != null) {
                        ocrResults.add("Data wystawienia: " + selectedDate);
                    } else {
                        ocrResults.add("Nie znaleziono daty wystawienia.");
                    }

                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    ocrResults.clear();
                    ocrResults.add("Błąd przetwarzania obrazu: " + e.getMessage());
                    adapter.notifyDataSetChanged();
                });
    }


    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(dp[i - 1][j] + 1,
                        Math.min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost));
            }
        }
        return dp[s1.length()][s2.length()];
    }


    private boolean isSimilar(String s1, String s2) {
        int distance = levenshteinDistance(s1, s2);
        int maxLen = Math.max(s1.length(), s2.length());
        double normalized = (double) distance / maxLen;

        return normalized < 0.3;
    }

    private Bitmap rotateBitmap(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

}




