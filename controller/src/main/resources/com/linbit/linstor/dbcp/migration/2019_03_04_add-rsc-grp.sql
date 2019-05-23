CREATE TABLE RESOURCE_GROUPS
(
    UUID CHARACTER(36) NOT NULL,
    RESOURCE_GROUP_NAME VARCHAR(48) NOT NULL,
    RESOURCE_GROUP_DSP_NAME VARCHAR(48) NOT NULL,
    DESCRIPTION VARCHAR(20) NULL, -- nullable
    LAYER_STACK VARCHAR(1024) NULL, -- nullable, json list of strings
    REPLICA_COUNT INT NULL, -- nullable, if null, this rscGrp is not usable with auto-place
    POOL_NAME VARCHAR(48) NULL, -- nullable, no foreign key, to allow referencing not yet or no longer existing storage pools
    DO_NOT_PLACE_WITH_RSC_REGEX VARCHAR(4096) NULL, -- nullable
    DO_NOT_PLACE_WITH_RSC_LIST VARCHAR(4096) NULL, -- nullable, json list of strings
    REPLICAS_ON_SAME VARCHAR(4096) NULL, -- nullable, json list of strings
    REPLICAS_ON_DIFFERENT BLOB NULL, -- nullable, json list of strings
    CONSTRAINT PK_RT PRIMARY KEY (RESOURCE_GROUP_NAME),
    CONSTRAINT UNQ_RT_UUID UNIQUE (UUID),
    CONSTRAINT CHK_RT_NAME CHECK (UPPER(RESOURCE_GROUP_NAME) = RESOURCE_GROUP_NAME AND LENGTH(RESOURCE_GROUP_NAME) >= 2),
    CONSTRAINT CHK_RT_DSP_NAME CHECK (UPPER(RESOURCE_GROUP_DSP_NAME) = RESOURCE_GROUP_NAME)
);

CREATE TABLE VOLUME_GROUPS
(
    UUID CHARACTER(36) NOT NULL,
    RESOURCE_GROUP_NAME VARCHAR(48) NOT NULL, -- part of PK; FK to RESOURCE_GROUPS
    VLM_NR INTEGER NOT NULL, -- part of PK
    CONSTRAINT PK_VT PRIMARY KEY (RESOURCE_GROUP_NAME, VLM_NR),
    CONSTRAINT FK_VT_RT FOREIGN KEY (RESOURCE_GROUP_NAME) REFERENCES RESOURCE_GROUPS(RESOURCE_GROUP_NAME) ON DELETE CASCADE,
    CONSTRAINT UNQ_RT_UUID UNIQUE (UUID)
)