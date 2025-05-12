module org.jens.exiftooleditor {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires org.kordamp.bootstrapfx.core;

    opens org.jens.exiftooleditor to javafx.fxml;
    exports org.jens.exiftooleditor;
}