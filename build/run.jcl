//BURGESS  JOB ,NOTIFY=&SYSUID,
// MSGCLASS=H,MSGLEVEL=(1,1),REGION=144M
//*
//*
//* Set the HLQ symbolic which must contain the same HLQ
//* as where the SAMPLE.* datasets reside
//*
//    SET HLQ='BURGESS'                       *TSO USER ID
//*
//*
//*************************
//* CLEAN UP
//*************************
//DELETE   EXEC PGM=IEFBR14
//SYSPRINT DD SYSOUT=*
//SYSOUT   DD SYSOUT=*
//SYSUDUMP DD SYSOUT=*
//DD1      DD DSN=&HLQ..SAMPLE.CUSTRPT,DISP=(MOD,DELETE,DELETE),
//            UNIT=SYSDA,SPACE=(CYL,(0))
//DD2      DD DSN=&HLQ..SAMPLE.CUSTOUT,DISP=(MOD,DELETE,DELETE),
//            UNIT=SYSDA,SPACE=(CYL,(0))
/*
//*************************
//* RUN SAM1
//*************************
//SAM1  EXEC   PGM=SAM1
//STEPLIB DD DSN=&HLQ..SAMPLE.LOAD,DISP=SHR
//*
//SYSOUT   DD SYSOUT=*
//SYSUDUMP DD SYSOUT=*
//*
//* INPUT CUSTOMER FILE
//CUSTFILE DD DISP=SHR,DSN=&HLQ..SAMPLE.CUSTFILE
//*
//* INPUT TRANSACTION FILE
//TRANFILE DD DISP=SHR,DSN=&HLQ..SAMPLE.TRANFILE
//*
//* NEW CUSTOMER FILE
//CUSTOUT  DD DSN=&HLQ..SAMPLE.CUSTOUT,
//    DISP=(NEW,CATLG),UNIT=SYSDA,SPACE=(TRK,(10,10),RLSE),
//    DSORG=PS,RECFM=VB,LRECL=600,BLKSIZE=604
//*
//* OUTPUT CUSTRPT FILE
//CUSTRPT  DD DSN=&HLQ..SAMPLE.CUSTRPT,
//    DISP=(NEW,CATLG),UNIT=SYSDA,SPACE=(TRK,(10,10),RLSE),
//    DSORG=PS,RECFM=FB,LRECL=133,BLKSIZE=0