package com.pdfmanager.db;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pdfmanager.files.Book;
import com.pdfmanager.files.ClassNote;
import com.pdfmanager.files.Slide;
import io.restassured.path.json.JsonPath;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.pdfmanager.cli.UserInterface.*;

public class DatabaseManager {

    private final File configPath;
    private final File booksPath;
    private final File slidesPath;
    private final File classNotesPath;

    public DatabaseManager() {
        this.configPath = new File(Objects.requireNonNull(getClass().getResource("/config.json")).getPath());
        this.booksPath = new File(Objects.requireNonNull(getClass().getResource("/books.json")).getPath());
        this.slidesPath = new File(Objects.requireNonNull(getClass().getResource("/slides.json")).getPath());
        this.classNotesPath = new File(Objects.requireNonNull(getClass().getResource("/classnotes.json")).getPath());
    }

    /**
     * Reads a value from a field in the database.
     * @param dbPath The database path.
     * @param field The field from which the value should be read.
     * @return Returns the value of the field if it's valid, returns <i>null</i> otherwise.
     * @throws IOException Might result in a IOException. Treatment required.
     */
    public String readField(File dbPath, String field) throws IOException {
        ObjectNode object = new ObjectMapper().readValue(dbPath, ObjectNode.class);
        JsonNode node = object.get(field);
        return (node == null ? null : node.textValue());
    }

    /**
     * Reads a file in the database and returns it as an object list
     * @param dbPath The path to the file
     * @return       The content of the file in List format
     * @throws IOException Throws an exception if file does not exist
     */
    public List<Object> readFileAsList(File dbPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return new ArrayList<>(Arrays.asList(mapper.readValue(dbPath, Object[].class)));
    }

    /**
     * Writes a value on a field in the database.
     * @param dbPath The database path.
     * @param field  The <i>field</i> in which the value should be written.
     * @param data   The <i>value</i> to be written.
     * @throws IOException Might result in a IOException. Treatment required.
     */
    public void writeField(File dbPath, String field, String data) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode object = mapper.readValue(dbPath, ObjectNode.class);
        object.put(field, data);
        mapper.writeValue(dbPath, object);
    }

    /**
     * Removes an entry from the database and returns a specific data from it.
     * @param dbPath The database path.
     * @param title The title of the file.
     * @param info The field from which to get the return value.
     * @return The data to be rescued from the database entry before deletion.
     * @throws IOException Might throw an exception if unable to manipulate database.
     */
    public String removeEntry(File dbPath, String title, String info) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode root = (ArrayNode) mapper.readTree(dbPath);
        boolean removed = false;
        String data = null;

        for (int i = root.size() - 1; i >= 0; i--) {
            JsonNode item = root.get(i);
            if (item.get("title").asText().equals(title)) {
                data = JsonPath.from(dbPath).get("find { it.title == '" + title +  "'}." + info);
                root.remove(i);
                removed = true;
            }
        }
        if (!removed) {
            System.out.println(RED + "Entry '" + title + "' not found in database." + RESET);
            return null;
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(dbPath, root);
        return data;
    }

    /**
     * Edits a specific field from an entry in the database.
     * @param dbPath The database path.
     * @param targetTitle The title of the targeted file in the database.
     * @throws IOException Might throw an exception if unable to manipulate database.
     */
    public void editFieldByTitle(File dbPath, String targetTitle) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Scanner scanner = new Scanner(System.in);

        ArrayNode db = (ArrayNode) mapper.readTree(dbPath);

        String jsonContent = mapper.writeValueAsString(db);
        JsonPath jsonPath = new JsonPath(jsonContent);

        Map<String, Object> targetMap = jsonPath.getMap("find { it.title == '" + targetTitle + "' }");

        if (targetMap == null) {
            System.out.println(RED + "ERROR: No object found with title: " + targetTitle + RESET);
            return;
        }

        System.out.print("Enter the field to edit (e.g., authors, path, subTitle):\n");
        String field = scanner.nextLine();

        if (!targetMap.containsKey(field)) {
            System.out.println(RED + "ERROR: Field does not exist in '" + targetTitle + "'" + RESET);
            return;
        } else if (field.equals("title")) {
            System.out.println(YELLOW + "You cannot edit the title of the file, try another field.\n" + RESET);
            editFieldByTitle(dbPath, targetTitle);
            return;
        }

        System.out.print("Enter the new value:\n");
        String newValue = scanner.nextLine();

        targetMap.put(field, newValue);

        for (int i = 0; i < db.size(); i++) {
            JsonNode node = db.get(i);
            if (node.has("title") && node.get("title").asText().equals(targetTitle)) {
                // Replace the node
                db.set(i, mapper.convertValue(targetMap, JsonNode.class));
                break;
            }
        }

        mapper.writerWithDefaultPrettyPrinter().writeValue(dbPath, db);

        System.out.println(GREEN + "Field updated successfully." + RESET);
    }

    /**
     * This function writes a Java class (<i>Book</i>, <i>Slide</i> or <i>ClassNote</i>) to their
     * specific files in the database.
     * @param buffer Is a Map of parameters the user typed in, it is used to initialize the desired class.
     *               The Map needs to contain a field <b>'type'</b> with the name of the class to be instanced.
     */
    public boolean writeObject(Map<String, Object> buffer) {
        if (!buffer.containsKey("type")) {
            System.err.println("ERROR: Missing 'type' key in Map<String, Object> buffer");
            return false;
        }

        if (buffer.get("type") == "Book") {
            addBookToDB(buffer);

        } else if (buffer.get("type") == "Slide") {
            addSlideToDB(buffer);

        } else if (buffer.get("type") == "ClassNote") {
            addClassNoteToDB(buffer);

        } else {
            System.err.println("ERROR: Invalid Document type.");
            return false;
        }

        return true;
    }

    /**
     * Adds a book to the database.
     * @param buffer A map with the information to be written in the database.
     */
    @SuppressWarnings("unchecked")
    private void addBookToDB(Map<String, Object> buffer) {
        ObjectMapper mapper = new ObjectMapper();
        List<Object> bookList;
        try {
            bookList = readFileAsList(booksPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Book book = new Book();
        book.setTitle((String) buffer.get("title"));
        book.setPath((String) buffer.get("path"));
        book.setAuthors((List<String>) buffer.get("authors"));
        book.setSubTitle((String) buffer.get("subTitle"));
        book.setFieldOfKnowledge((String) buffer.get("fieldOfKnowledge"));
        try {
            book.setPublishYear(Integer.parseInt((String) buffer.get("publishYear")));
        } catch (Exception e) {
            System.err.println("ERROR: Invalid input value. Value should be a integer.");
            System.err.flush();
        }

        bookList.add(book);
        try {
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(booksPath, bookList);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds a slide to the database.
     * @param buffer A map with the information to be written in the database.
     */
    @SuppressWarnings("unchecked")
    private void addSlideToDB(Map<String, Object> buffer) {
        ObjectMapper mapper = new ObjectMapper();
        List<Object> slideList;
        try {
            slideList = readFileAsList(slidesPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Slide slide = new Slide();
        slide.setTitle((String) buffer.get("title"));
        slide.setPath((String) buffer.get("path"));
        slide.setAuthors((List<String>) buffer.get("authors"));
        slide.setLectureName((String) buffer.get("lectureName"));
        slide.setInstitutionName((String) buffer.get("institutionName"));

        slideList.add(slide);
        try {
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(slidesPath, slideList);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds a class note to the database.
     * @param buffer A map with the information to be written in the database.
     */
    @SuppressWarnings("unchecked")
    private void addClassNoteToDB(Map<String, Object> buffer) {
        ObjectMapper mapper = new ObjectMapper();
        List<Object> classNoteList;
        try {
            classNoteList = readFileAsList(classNotesPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ClassNote classNote = new ClassNote();
        classNote.setTitle((String) buffer.get("title"));
        classNote.setPath((String) buffer.get("path"));
        classNote.setAuthors((List<String>) buffer.get("authors"));
        classNote.setSubTitle((String) buffer.get("subTitle"));
        classNote.setLectureName((String) buffer.get("lectureName"));
        classNote.setInstitutionName((String) buffer.get("institutionName"));

        classNoteList.add(classNote);
        try {
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(classNotesPath, classNoteList);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public File getConfigPath() {
        return configPath;
    }

    public File getBooksPath() {
        return booksPath;
    }

    public File getSlidesPath() {
        return slidesPath;
    }

    public File getClassNotesPath() {
        return classNotesPath;
    }

    public String getLibraryPath() throws IOException {
        return readField(configPath, "libraryPath");
    }
}
