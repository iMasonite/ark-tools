package qowyn.ark.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonGeneratorFactory;

import qowyn.ark.ArkSavegame;
import qowyn.ark.GameObject;
import qowyn.ark.properties.PropertyObject;
import qowyn.ark.types.ArkByteValue;
import qowyn.ark.types.LocationData;

public class AnimalListCommands {

  private static final Map<Integer, String> ATTRIBUTE_NAME_MAP = new HashMap<>();

  static {
    ATTRIBUTE_NAME_MAP.put(0, "health");
    ATTRIBUTE_NAME_MAP.put(1, "stamina");
    ATTRIBUTE_NAME_MAP.put(3, "oxygen");
    ATTRIBUTE_NAME_MAP.put(4, "food");
    ATTRIBUTE_NAME_MAP.put(7, "weight");
    ATTRIBUTE_NAME_MAP.put(8, "melee");
    ATTRIBUTE_NAME_MAP.put(9, "speed");
  }

  public static void animals(String[] args) {
    if (args.length < 2 || args.length > 3) {
      System.out.println("Usage: animals <save> <output_directory> [mapSize]");
      return;
    }
    
    int mapSize = args.length == 3 ? Integer.parseInt(args[2]) : 8000;

    try {
      Instant start = Instant.now();
      ArkSavegame saveFile = new ArkSavegame(args[0]);
      Instant readFinished = Instant.now();
      writeAnimalLists(args[1], saveFile, mapSize);
      Instant dumpFinished = Instant.now();

      System.out.println("Reading finshed after " + ChronoUnit.MILLIS.between(start, readFinished) + " ms");
      System.out.println("Dump finshed after " + ChronoUnit.MILLIS.between(readFinished, dumpFinished) + " ms");
      System.out.println("Completly finshed after " + ChronoUnit.MILLIS.between(start, dumpFinished) + " ms");
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static void tamed(String[] args) {
    if (args.length < 2 || args.length > 3) {
      System.out.println("Usage: tamed <save> <output_directory> [mapSize]");
      return;
    }
    
    int mapSize = args.length == 3 ? Integer.parseInt(args[2]) : 8000;

    try {
      Instant start = Instant.now();
      ArkSavegame saveFile = new ArkSavegame(args[0]);
      Instant readFinished = Instant.now();
      writeAnimalLists(args[1], saveFile, mapSize, CommonFunctions::onlyTamed);
      Instant dumpFinished = Instant.now();

      System.out.println("Reading finshed after " + ChronoUnit.MILLIS.between(start, readFinished) + " ms");
      System.out.println("Dump finshed after " + ChronoUnit.MILLIS.between(readFinished, dumpFinished) + " ms");
      System.out.println("Completly finshed after " + ChronoUnit.MILLIS.between(start, dumpFinished) + " ms");
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static void wild(String[] args) {
    if (args.length < 2 || args.length > 3) {
      System.out.println("Usage: wild <save> <output_directory> [mapSize]");
      return;
    }
    
    int mapSize = args.length == 3 ? Integer.parseInt(args[2]) : 8000;

    try {
      Instant start = Instant.now();
      ArkSavegame saveFile = new ArkSavegame(args[0]);
      Instant readFinished = Instant.now();
      writeAnimalLists(args[1], saveFile, mapSize, CommonFunctions::onlyWild);
      Instant dumpFinished = Instant.now();

      System.out.println("Reading finshed after " + ChronoUnit.MILLIS.between(start, readFinished) + " ms");
      System.out.println("Dump finshed after " + ChronoUnit.MILLIS.between(readFinished, dumpFinished) + " ms");
      System.out.println("Completly finshed after " + ChronoUnit.MILLIS.between(start, dumpFinished) + " ms");
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static void writeAnimalLists(String outputDirectory, ArkSavegame saveFile, int mapSize) {
    writeAnimalLists(outputDirectory, saveFile, mapSize, null);
  }

  public static void writeAnimalLists(String outputDirectory, ArkSavegame saveFile, int mapSize, Predicate<GameObject> filter) {
    List<GameObject> objects;

    if (filter != null) {
      objects = saveFile.getObjects().stream().filter(filter).collect(Collectors.toList());
    } else {
      objects = saveFile.getObjects();
    }

    Map<String, String> classNames = readClassNames(outputDirectory);

    ConcurrentMap<String, List<GameObject>> dinoLists = objects.parallelStream()
        .filter(go -> go.getClassString().contains("_Character_"))
        .collect(Collectors.groupingByConcurrent(GameObject::getClassString));

    dinoLists.keySet().forEach(dinoClass -> classNames.putIfAbsent(dinoClass, dinoClass));

    writeClassNames(outputDirectory, classNames);

    dinoLists.entrySet().parallelStream().forEach(e -> writeList(e, outputDirectory, saveFile, mapSize));
  }

  public static Map<String, String> readClassNames(String directory) {
    Path classFile = Paths.get(directory, "classes.json");
    Map<String, String> classNames = new HashMap<>();

    if (Files.exists(classFile)) {
      try (InputStream classStream = Files.newInputStream(classFile)) {
        JsonReader classReader = Json.createReader(classStream);
        JsonArray classArray = classReader.readArray();
        for (JsonObject o : classArray.getValuesAs(JsonObject.class)) {
          String cls = o.getString("cls");
          String name = o.getString("name");
          if (!classNames.containsKey(cls)) {
            classNames.put(cls, name);
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    return classNames;
  }

  public static void writeClassNames(String directory, Map<String, String> classNames) {
    Path clsFile = Paths.get(directory, "classes.json");

    try (OutputStream clsStream = Files.newOutputStream(clsFile)) {
      JsonArrayBuilder clsBuilder = Json.createArrayBuilder();

      classNames.entrySet().forEach(cls -> clsBuilder.add(Json.createObjectBuilder().add("cls", cls.getKey()).add("name", cls.getValue())));

      Map<String, Object> properties = new HashMap<>(1);
      properties.put(JsonGenerator.PRETTY_PRINTING, true);

      JsonWriterFactory clsFactory = Json.createWriterFactory(properties);
      JsonWriter clsWriter = clsFactory.createWriter(clsStream);
      clsWriter.writeArray(clsBuilder.build());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void writeList(Map.Entry<String, List<GameObject>> entry, String outputDirectory, ArkSavegame saveFile, int mapSize) {
    Path outputFile = Paths.get(outputDirectory, entry.getKey() + ".json");

    List<? extends GameObject> filteredClasses = entry.getValue();

    try {
      PrintWriter writer = new PrintWriter(outputFile.toFile());

      Map<String, Object> properties = new HashMap<>(1);
      properties.put(JsonGenerator.PRETTY_PRINTING, true);

      JsonGeneratorFactory factory = Json.createGeneratorFactory(properties);

      JsonGenerator generator = factory.createGenerator(writer);
      generator.writeStartArray();

      for (GameObject i : filteredClasses) {
        generator.writeStartObject();

        LocationData ld = i.getLocation();
        if (ld != null) {
          generator.write("x", ld.getX());
          generator.write("y", ld.getY());
          generator.write("z", ld.getZ());
          generator.write("lat", Math.round(ld.getLat(mapSize) * 10.0) / 10.0);
          generator.write("lon", Math.round(ld.getLon(mapSize) * 10.0) / 10.0);
        }

        if (i.hasAnyProperty("bIsFemale")) {
          generator.write("female", true);
        }

        if (i.hasAnyProperty("TamedAtTime")) {
          generator.write("tamed", true);
        }

        String name = i.getPropertyValue("TamedName", String.class);
        if (name != null) {
          generator.write("name", name);
        }

        PropertyObject statusComp = i.getTypedProperty("MyCharacterStatusComponent", PropertyObject.class);
        GameObject status = null;
        if (statusComp != null) {
          status = statusComp.getValue().getObject(saveFile);
        }

        if (status != null && status.getClassString().startsWith("DinoCharacterStatusComponent_")) {
          Integer baseLevel = status.getPropertyValue("BaseCharacterLevel", Integer.class);
          if (baseLevel != null) {
            generator.write("baseLevel", baseLevel);
          }

          if (baseLevel != null && baseLevel > 1) {
            generator.writeStartObject("wildLevels");
            for (Map.Entry<Integer, String> attribute : ATTRIBUTE_NAME_MAP.entrySet()) {
              ArkByteValue attrProp = status.getPropertyValue("NumberOfLevelUpPointsApplied", ArkByteValue.class, attribute.getKey());
              if (attrProp != null) {
                generator.write(attribute.getValue(), attrProp.getByteValue());
              }
            }
            generator.writeEnd();
          }

          if (status.hasAnyProperty("NumberOfLevelUpPointsAppliedTamed")) {
            generator.writeStartObject("tamedLevels");
            for (Map.Entry<Integer, String> attribute : ATTRIBUTE_NAME_MAP.entrySet()) {
              ArkByteValue attrProp = status.getPropertyValue("NumberOfLevelUpPointsAppliedTamed", ArkByteValue.class, attribute.getKey());
              if (attrProp != null) {
                generator.write(attribute.getValue(), attrProp.getByteValue());
              }
            }
            generator.writeEnd();
          }
        }

        generator.writeEnd();
      }

      generator.writeEnd();
      generator.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}