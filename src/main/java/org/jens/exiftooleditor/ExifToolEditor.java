package org.jens.exiftooleditor;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.scene.control.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

public class ExifToolEditor extends Application {
    private static final String EXIFTOOL_PATH = "/opt/homebrew/bin/exiftool";
    private static final File DEFAULT_IMAGE_DIR = new File("/Users/jensfreudenau/Pictures");

    private File[] selectedImages;

    private final ComboBox<String> categoryBox = new ComboBox<>();
    private final CheckBox multilineKeywords = new CheckBox("Ein Schlagwort pro Zeile");
    private final TextArea keywordsArea = new TextArea();
    private final TextField titleField = new TextField();
    private final TextArea descriptionArea = new TextArea();
    private final TextField websiteField = new TextField();
    private final Label imageLabel = new Label("Kein Bild ausgewählt");

    public static void main(String[] args) {
        launch(args);
    }
    @Override
    public void start(Stage stage) throws IOException {
        stage.setTitle("IPTC Metadaten-Editor (ExifTool)");

        categoryBox.getItems().addAll(
                "UNK - unbekannt",
                "ARC - Architektur",
                "ART - Artist",
                "CHZ - Schweiz",
                "COD - Code",
                "FRA - Frankreich",
                "GER - Deutschland",
                "GES - Gesellschaft",
                "ITA - Italien",
                "KUL - Kultur",
                "LAN - Landschaft",
                "NAC - Nacht",
                "NAT - Natur",
                "PAR - Park",
                "POL - Politik",
                "REI - Reisen",
                "SAD - Server Administration",
                "SIG - Zeichen",
                "SPA - Spain",
                "SPO - Sport",
                "STR - street",
                "WIR - Wirtschaft"
        );

        GridPane form = new GridPane();
        form.setPadding(new Insets(10));
        form.setHgap(10);
        form.setVgap(10);

        addField(form, 0, "Kategorie:", categoryBox);
        addField(form, 1, "Überschrift:", titleField);
        addArea(form, 2, "Beschreibung:", descriptionArea);
        addField(form, 3, "Webseite:", websiteField);
        addArea(form, 4, "Schlagwörter:", keywordsArea);
        form.add(multilineKeywords, 1, 5);

        Button loadButton = new Button("Bild laden");
        Button saveButton = new Button("Metadaten speichern");

        HBox buttonBox = new HBox(10, loadButton, saveButton, imageLabel);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        buttonBox.setPadding(new Insets(10));

        VBox root = new VBox(form, buttonBox);
        Scene scene = new Scene(root, 800, 600);
        stage.setTitle("Exif Tool Keyword Editor");
        stage.setScene(scene);
        stage.show();

        loadButton.setOnAction(e -> loadImage(stage));
        saveButton.setOnAction(e -> saveMetadata());
    }

    private void addField(GridPane pane, int row, String label, Control field) {
        pane.add(new Label(label), 0, row);
        pane.add(field, 1, row);
        if (field instanceof TextField) {
            ((TextField) field).setPrefWidth(600);
        }
    }

    private void addArea(GridPane pane, int row, String label, TextArea area) {
        area.setWrapText(true);
        area.setPrefRowCount(4);
        pane.add(new Label(label), 0, row);
        pane.add(area, 1, row);
    }
    private void loadImage(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Bild auswählen");
        fileChooser.setInitialDirectory(DEFAULT_IMAGE_DIR);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Bilder", "*.jpg", "*.jpeg", "*.tif", "*.tiff"));
        List<File> files = fileChooser.showOpenMultipleDialog(stage);

        if (files != null && !files.isEmpty()) {
            selectedImages = files.toArray(new File[0]);
            imageLabel.setText(selectedImages.length + " Bilder ausgewählt");
            loadMetadataFromFile(selectedImages[0]);
        }
    }
    private void loadMetadataFromFile(File image) {
        try {
            String categoryValue = readTag(image, List.of("IPTC:Category", "XMP:Category"));
            categoryBox.getItems().stream()
                    .filter(item -> item.startsWith(categoryValue))
                    .findFirst().ifPresent(categoryBox::setValue);

            String keywordsRaw = readTag(image, List.of("IPTC:Keywords", "XMP-dc:Subject"));
            if (multilineKeywords.isSelected()) {
                keywordsArea.setText(keywordsRaw.replace(", ", "\n"));
            } else {
                keywordsArea.setText(keywordsRaw);
            }

            titleField.setText(readTag(image, List.of("IPTC:ObjectName", "XMP-dc:Title")));
            descriptionArea.setText(readTag(image, List.of("IPTC:Caption-Abstract", "XMP-dc:Description")));
            websiteField.setText(readTag(image, List.of("IPTC:Source", "XMP-dc:Source", "XMP:URL")));

        } catch (Exception ex) {
            showError("Fehler beim Lesen:\n" + ex.getMessage());
        }
    }

    private void saveMetadata() {
        if (selectedImages == null || selectedImages.length == 0) {
            showError("Bitte zuerst ein Bild laden.");
            return;
        }

        try {
            String selectedCat = categoryBox.getValue().split(" - ")[0];
            String[] keywords = multilineKeywords.isSelected()
                    ? keywordsArea.getText().split("\\r?\\n")
                    : keywordsArea.getText().split(",");

            for (File imageFile : selectedImages) {
                List<String> command = new ArrayList<>();
                command.add(EXIFTOOL_PATH);
                command.add("-overwrite_original");
                command.add("-IPTC:Keywords=");
                command.add("-XMP-dc:Subject=");

                for (String kw : keywords) {
                    kw = kw.trim();
                    if (!kw.isEmpty()) {
                        command.add("-IPTC:Keywords=" + kw);
                        command.add("-XMP-dc:Subject=" + kw);
                    }
                }

                command.add("-IPTC:ObjectName=" + titleField.getText().trim());
                command.add("-XMP-dc:Title=" + titleField.getText().trim());

                command.add("-IPTC:Caption-Abstract=" + descriptionArea.getText().trim());
                command.add("-XMP-dc:Description=" + descriptionArea.getText().trim());

                command.add("-IPTC:Category=" + selectedCat);
                command.add("-XMP:Category=" + selectedCat);
                command.add("-XMP-dc:Subject+=" + selectedCat);

                command.add("-IPTC:Source=" + websiteField.getText().trim());
                command.add("-XMP-dc:Source=" + websiteField.getText().trim());
                command.add("-XMP:URL=" + websiteField.getText().trim());

                command.add(imageFile.getAbsolutePath());

                ProcessBuilder builder = new ProcessBuilder(command);
                builder.redirectErrorStream(true);
                Process process = builder.start();
                process.waitFor();
            }

            showInfo(selectedImages.length + " Bilddateien wurden aktualisiert.");

        } catch (Exception ex) {
            showError("Fehler beim Schreiben:\n" + ex.getMessage());
        }
    }

    private String readTag(File imageFile, List<String> possibleTags) throws IOException {
        for (String tag : possibleTags) {
            ProcessBuilder builder = new ProcessBuilder(EXIFTOOL_PATH, "-" + tag, imageFile.getAbsolutePath());
            Process process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    if (parts[0].trim().equalsIgnoreCase(tag.split(":")[1])) {
                        return parts[1].trim();
                    }
                }
            }
        }
        return "";
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setTitle("Fehler");
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setTitle("Fertig");
        alert.showAndWait();
    }
}
