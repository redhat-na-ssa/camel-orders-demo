DROP TABLE IF EXISTS sampledb.ITEM_DESCRIPTION;

CREATE TABLE sampledb.ITEM_DESCRIPTION(
  ID VARCHAR(36) NOT NULL,
  DESCRIPTION VARCHAR(256) NOT NULL,
  PRIMARY KEY (ID)
);