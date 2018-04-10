/*
 * This file is part of Negatron.
 * Copyright (C) 2015-2018 BabelSoft S.A.S.U.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.babelsoft.negatron.preloader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.application.Preloader;
import javafx.application.Preloader.ProgressNotification;
import javafx.application.Preloader.StateChangeNotification;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/**
 * Simple Preloader Using the ProgressBar Control
 *
 * @author capan
 */
public class NegatronPreloader extends Preloader {
    
    private static final Path NEGATRON_INI = Paths.get("./Negatron.ini");
    private static final PseudoClass ERROR_CLASS = PseudoClass.getPseudoClass("error");
    private static final String MAME = "MAME";
    private static final String MESS = "MESS";
    
    private static class Configuration {
        private final String mamePath;
        private final String extrasPath;
        private final String multimediaPath;
        private final String language;
        
        private Configuration(String mamePath, String extrasPath, String multimediaPath, String language) {
            this.mamePath = mamePath;
            this.extrasPath = extrasPath;
            this.multimediaPath = multimediaPath;
            this.language = language;
        }
    }
    
    public static interface Notifier {
        public void onConfigurationSucceeded();
        public void onPreloadingSucceeded();
    }

    private Notifier notifier;
    private ProgressBar bar;
    private Label label;
    private Stage stage;
    private ResourceBundle language;
    
    private boolean isMess;

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        
        if (Files.exists(NEGATRON_INI)) {
            Files.lines(NEGATRON_INI).filter(
                line -> line.startsWith("language ")
            ).findAny().ifPresent(line -> {
                String lang = line.substring(9).trim();
                Locale.setDefault(Locale.forLanguageTag(lang));
            });
        }
        language = Language.Manager.getBundle();
        
        // Prerequisite check
        float javaVersion = Float.parseFloat(System.getProperty("java.specification.version"));
        String javaFxVersion = System.getProperty("javafx.version");
        if (javaVersion < 1.8f || javaFxVersion == null || javaFxVersion.trim().equals("")) {
            AlertBox alert = AlertBox.showAndWait(String.format(language.getString("javaVersion.error"), javaVersion)
            );
            alert.dispose();
            throw new RuntimeException("You need to run Java 1.8+");
        }
        String[] version = javaFxVersion.split("\\.");
        int versionMajor = Integer.parseInt(version[0]);
        if (
            versionMajor < 8 ||
            versionMajor == 8 &&
            version.length > 1 && Integer.parseInt(version[1]) == 0 &&
            version.length > 2 && Integer.parseInt(version[2]) < 60
        ) {
            AlertBox alert = AlertBox.showAndWait(String.format(language.getString("javaFxVersion.error"), javaFxVersion)
            );
            alert.dispose();
            throw new RuntimeException("You need to run Java 8.0.60+");
        }
        
        // for whatever reasons, maxMemory() doesn't return the real -Xmx value: with Java 8u66, on Windows 455MB instead of 512, on Linux 488MB instead of 512.
        long maxHeapMB = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        if (0 < maxHeapMB && maxHeapMB <= 450) {
            Alert alert = new Alert(
                AlertType.ERROR,
                String.format(language.getString("maxMemory.error"), maxHeapMB),
                ButtonType.CLOSE
            );
            alert.showAndWait();
            throw new RuntimeException("You need to allow at least 512MB of memory to Negatron");
        }
        
        // Set the stage up
        stage.setTitle(language.getString("welcome"));
        
        stage.getIcons().add(new Image(NegatronPreloader.class.getResourceAsStream("resource/Negatron.16.png")));
        stage.getIcons().add(new Image(NegatronPreloader.class.getResourceAsStream("resource/Negatron.32.png")));
        stage.getIcons().add(new Image(NegatronPreloader.class.getResourceAsStream("resource/Negatron.64.png")));
        
        stage.setScene(createPreloaderScene());
        stage.show();
        
        // Initialisation check
        if (Files.notExists(NEGATRON_INI)) {
            label.setText(language.getString("configuring..."));
            
            Dialog<Configuration> dialog = createPromptDialog();
            dialog.initOwner(stage);
            dialog.showAndWait().ifPresent(mameConfiguration -> {
                try (BufferedWriter writer = Files.newBufferedWriter(NEGATRON_INI)) {
                    String path = mameConfiguration.mamePath;
                    if (path.contains(" "))
                        path = "\"" + path + "\"";
                    if (isMess)
                        writer.write("mess                      ");
                    else
                        writer.write("mame                      ");
                    writer.write(path); writer.newLine();
                    writer.write("extras                    "); writer.write(mameConfiguration.extrasPath); writer.newLine();
                    writer.write("multimedia                "); writer.write(mameConfiguration.multimediaPath); writer.newLine();
                    writer.write("language                  "); writer.write(mameConfiguration.language); writer.newLine();
                } catch (IOException ex) {
                    Logger.getLogger(NegatronPreloader.class.getName()).log(Level.SEVERE, "Couldn't write ini file", ex);
                }
            });
        }
        
        label.setText(language.getString("loading..."));
    }

    private Scene createPreloaderScene() throws IOException {
        ImageView image = new ImageView(new Image(getClass().getResourceAsStream(
            "resource/NegatronLogo.png"
        )));
        
        bar = new ProgressBar();
        label = new Label();
        VBox vbox = new VBox();
        vbox.setAlignment(Pos.CENTER);
        vbox.setCenterShape(true);
        vbox.setSpacing(5.0);
        vbox.getChildren().add(image);
        vbox.getChildren().add(bar);
        vbox.getChildren().add(label);
        Scene scene = new Scene(vbox, 300, 150);
        
        if (Files.exists(NEGATRON_INI)) {
            Files.lines(NEGATRON_INI).filter(
                line -> line.startsWith("skin ")
            ).findAny().ifPresent(line -> {
                String skin = line.substring(5).trim();
                if (skin.length() != 0) {
                    Path css = Paths.get("theme/skin/" + skin + "/skin.css");
                    if (Files.exists(css))
                        scene.getStylesheets().setAll(css.toUri().toString());
                }
            });
        }
        
        return scene;
    }
    
    private Dialog<Configuration> createPromptDialog() {
        Dialog<Configuration> dialog = new Dialog<>();
        dialog.setTitle(language.getString("configuration"));
        dialog.setHeaderText(language.getString("configuration.text"));
        dialog.setGraphic(new ImageView(getClass().getResource("resource/MAME.png").toExternalForm()));
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("PromptDialog.css").toExternalForm());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10.0);
        grid.setVgap(10.0);
        grid.setPadding(new Insets(20.0, 10.0, 10.0, 10.0));
        grid.setMinWidth(500.0);
        
        Label mameLabel = new Label(MAME);
        TextField mamePath = new TextField();
        Button mameButton = new Button(language.getString("browse..."));
        Label extrasLabel = new Label("MAME EXTRAs");
        TextField extrasPath = new TextField();
        Button extrasButton = new Button(language.getString("browse..."));
        Label multimediaLabel = new Label("MAME Multimedia");
        TextField multimediaPath = new TextField();
        Button multimediaButton = new Button(language.getString("browse..."));
        Label languageLabel = new Label(language.getString("language"));
        ChoiceBox<Locale> languageChoice = new ChoiceBox<>();
        
        // Initialise MAME path controls
        mamePath.setPromptText(language.getString("mame.prompt"));
        mamePath.pseudoClassStateChanged(ERROR_CLASS, true);
        mamePath.textProperty().addListener((o, oV, newValue) -> {
            if (newValue == null || newValue.trim().isEmpty())
                mamePath.pseudoClassStateChanged(ERROR_CLASS, true);
            else
                mamePath.pseudoClassStateChanged(ERROR_CLASS, false);
        });
        
        mameButton.setOnAction(event -> {
            Control c = (Control) event.getSource();
            FileChooser fc = new FileChooser();
            fc.setInitialDirectory(new File("."));
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter(MAME, "*mame*.exe"),
                    new FileChooser.ExtensionFilter(MESS, "*mess*.exe")
                );
            } else { // Mac OS X or Linux
                fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter(MAME, "*mame*"),
                    new FileChooser.ExtensionFilter(MESS, "*mess*")
                );
            }
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(language.getString("allFiles"), "*")
            );
            File f = fc.showOpenDialog(c.getScene().getWindow());
            if (f != null) {
                if (f.getName().contains("mess")) {
                    dialog.setHeaderText(dialog.getHeaderText().replace(MAME, MESS));
                    dialog.setGraphic(new ImageView(getClass().getResource("resource/MESS.png").toExternalForm()));
                    mameLabel.setText(MESS);
                    mamePath.setPromptText(mamePath.getPromptText().replace(MAME, MESS));
                    extrasLabel.setText("MESS EXTRAs");
                    extrasPath.setPromptText(extrasPath.getPromptText().replace(MAME, MESS));
                    multimediaLabel.setVisible(false);
                    multimediaPath.setVisible(false);
                    multimediaButton.setVisible(false);
                    isMess = true;
                } else {
                    dialog.setHeaderText(dialog.getHeaderText().replace(MESS, MAME));
                    dialog.setGraphic(new ImageView(getClass().getResource("resource/MAME.png").toExternalForm()));
                    mameLabel.setText(MAME);
                    mamePath.setPromptText(mamePath.getPromptText().replace(MESS, MAME));
                    extrasLabel.setText("MAME EXTRAs");
                    extrasPath.setPromptText(extrasPath.getPromptText().replace(MESS, MAME));
                    multimediaLabel.setVisible(true);
                    multimediaPath.setVisible(true);
                    multimediaButton.setVisible(true);
                    isMess = false;
                }
                mamePath.setText(f.getAbsolutePath());
            }
        });
        
        // Initialise EXTRAs path controls
        extrasPath.setPromptText(language.getString("extras.prompt"));
        extrasButton.setOnAction(event -> {
            Control c = (Control) event.getSource();
            DirectoryChooser dc = new DirectoryChooser();
            if (isMess)
                dc.setTitle("MESS EXTRAs");
            else
                dc.setTitle("MAME EXTRAs");
            if (!mamePath.getText().trim().isEmpty())
                dc.setInitialDirectory(Paths.get(mamePath.getText().trim()).getParent().toFile());
            else
                dc.setInitialDirectory(new File("."));
            File f = dc.showDialog(c.getScene().getWindow());
            if (f != null)
                extrasPath.setText(f.getAbsolutePath());
        });
        
        // Initialise Multimedia path controls
        multimediaPath.setPromptText(language.getString("multimedia.prompt"));
        multimediaButton.setOnAction(event -> {
            Control c = (Control) event.getSource();
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("MAME Multimedia");
            if (!extrasPath.getText().trim().isEmpty())
                dc.setInitialDirectory(Paths.get(extrasPath.getText().trim()).getParent().toFile());
            else if (!mamePath.getText().trim().isEmpty())
                dc.setInitialDirectory(Paths.get(mamePath.getText().trim()).getParent().toFile());
            else
                dc.setInitialDirectory(new File("."));
            File f = dc.showDialog(c.getScene().getWindow());
            if (f != null)
                multimediaPath.setText(f.getAbsolutePath());
        });
        
        // Initialise language controls
        languageChoice.getItems().add(Locale.UK);
        Path path = Paths.get(Language.Manager.ROOT_PATH);
        if (Files.exists(path)) try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String filename = file.getFileName().toString();
                    if (filename.matches(Language.Manager.MASK)) {
                        int i = filename.indexOf('_');
                        if (i >= 0) {
                            String locale = filename.substring(i + 1, filename.length() - 11).replace('_', '-');
                            languageChoice.getItems().add(Locale.forLanguageTag(locale));
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                    if (e == null)
                        return FileVisitResult.CONTINUE;
                    else
                        throw e; // directory iteration failed
                }
            });
        } catch (IOException ex) { } // swallow exceptions
        
        languageChoice.setConverter(new StringConverter<Locale>() {
            @Override
            public String toString(Locale locale) {
                return locale.getDisplayName(locale);
            }

            @Override
            public Locale fromString(String string) { // should never be called
                throw new UnsupportedOperationException("Not supported yet.");
            }
        });
        
        Locale locale = Locale.getDefault();
        if (languageChoice.getItems().contains(locale))
            languageChoice.getSelectionModel().select(locale);
        else
            languageChoice.getSelectionModel().select(Locale.UK);
        
        languageChoice.getSelectionModel().selectedItemProperty().addListener((o, oV, newValue) -> {
            Locale.setDefault(newValue);
            language = Language.Manager.getBundle();
            
            stage.setTitle(language.getString("welcome"));
            label.setText(language.getString("configuring..."));
            dialog.setTitle(language.getString("configuration"));
            dialog.setHeaderText(language.getString("configuration.text"));
            mamePath.setPromptText(language.getString("mame.prompt"));
            mameButton.setText(language.getString("browse..."));
            extrasPath.setPromptText(language.getString("extras.prompt"));
            extrasButton.setText(language.getString("browse..."));
            multimediaPath.setPromptText(language.getString("multimedia.prompt"));
            multimediaButton.setText(language.getString("browse..."));
            languageLabel.setText(language.getString("language"));
        });

        // Finish setting up dialog box
        ColumnConstraints constraints = new ColumnConstraints();
        constraints.setHgrow(Priority.SOMETIMES);
        grid.getColumnConstraints().add(new ColumnConstraints());
        grid.getColumnConstraints().add(constraints);
        grid.add(mameLabel, 0, 0);
        grid.add(mamePath, 1, 0);
        grid.add(mameButton, 2, 0);
        grid.add(extrasLabel, 0, 1);
        grid.add(extrasPath, 1, 1);
        grid.add(extrasButton, 2, 1);
        grid.add(multimediaLabel, 0, 2);
        grid.add(multimediaPath, 1, 2);
        grid.add(multimediaButton, 2, 2);
        grid.add(languageLabel, 0, 3);
        grid.add(languageChoice, 1, 3, 2, 1);

        Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(true);
        mamePath.textProperty().addListener((o, oV, newValue) -> {
            okButton.setDisable(newValue == null || newValue.trim().isEmpty());
        });
        dialog.getDialogPane().setContent(grid);

        Platform.runLater(() -> mamePath.requestFocus());

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK)
                return new Configuration(
                    mamePath.getText(),
                    extrasPath.getText(),
                    multimediaPath.getText(),
                    languageChoice.getSelectionModel().getSelectedItem().toLanguageTag()
                );
            else
                return null;
        });
        
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            // detect any packaged mame already installed
            ProcessBuilder pb = new ProcessBuilder("which", "mame");
            try (
                InputStream input = pb.start().getInputStream();
                InputStreamReader stream = new InputStreamReader(input);
                BufferedReader reader = new BufferedReader(stream);
            ) {
                mamePath.setText(reader.readLine());
            } catch (IOException ex) {
                Logger.getLogger(NegatronPreloader.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        return dialog;
    }
    
    @Override
    public void handleStateChangeNotification(StateChangeNotification evt) {
        if (evt.getType() == StateChangeNotification.Type.BEFORE_INIT) {
            notifier = (Notifier) evt.getApplication();
            notifier.onConfigurationSucceeded();
        }
    }   

    @Override
    public void handleApplicationNotification(PreloaderNotification pn) {
        if (pn instanceof ProgressNotification) {
            //expect application to send us progress notifications 
            //with progress ranging from 0 to 1.0
            double v = ((ProgressNotification) pn).getProgress();
            if (v < 0.99) {
                bar.setProgress(v);
                label.setText(language.getString("processingMameInput...").replace(MAME, isMess ? MESS : MAME));
            } else {
                bar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                label.setText(language.getString("formattingData..."));
            }
        } else if (pn instanceof StateChangeNotification) {
            //hide after get any state update from application
            stage.hide();
            notifier.onPreloadingSucceeded();
        } else if (pn instanceof ErrorNotification) {
            ErrorNotification error = (ErrorNotification) pn;
            
            Alert alert = new Alert(
                Alert.AlertType.ERROR,
                error.getLocation() + ":\n\n" +
                error.getCause().toString() + "\n\n" +
                error.getDetails(),
                ButtonType.CLOSE
            );
            alert.initOwner(stage);
            alert.showAndWait();
            
            stage.close();
        }
    }
}
