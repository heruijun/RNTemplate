package com.rntemplate.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Created by heruijun on 2017/6/21.
 */

public class KCZip {

    /**
     * get file list in zip
     *
     * @param aZipFile      zip path
     * @param isContainDir  is contain dir
     * @param isContainFile is contain file
     * @return
     * @throws Exception
     */
    public static List<File> getFileList(String aZipFile, boolean isContainDir, boolean isContainFile) throws Exception {
        List<File> fileList = new ArrayList();
        ZipInputStream inZip = new ZipInputStream(new FileInputStream(aZipFile));
        ZipEntry zipEntry;
        String szName = "";

        while ((zipEntry = inZip.getNextEntry()) != null) {
            szName = zipEntry.getName();

            if (zipEntry.isDirectory()) {
                // get the folder name of the widget
                szName = szName.substring(0, szName.length() - 1);
                File folder = new File(szName);
                if (isContainDir) {
                    fileList.add(folder);
                }
            } else {
                File file = new File(szName);
                if (isContainFile) {
                    fileList.add(file);
                }
            }
        }
        inZip.close();
        return fileList;
    }

    /**
     * return InputStream of file in zip
     *
     * @param aZipFile  zip file
     * @param aFileName file name of will unzip file
     * @return InputStream
     * @throws Exception
     */
    public static InputStream upZip(String aZipFile, String aFileName) throws Exception {
        android.util.Log.v("Zip", "upZip(String, String)");
        ZipFile zipFile = new ZipFile(aZipFile);
        ZipEntry zipEntry = zipFile.getEntry(aFileName);

        return zipFile.getInputStream(zipEntry);
    }

    /**
     * unzip to out path
     *
     * @param aZipFile    zip file
     * @param aOutDirPath out path
     * @throws Exception
     */
    public static void unZipToDir(File aZipFile, File aOutDirPath) throws Exception {
        ZipInputStream inZip = new ZipInputStream(new FileInputStream(aZipFile));
        ZipEntry zipEntry;
        String szName = "";

        if (!aOutDirPath.exists()) {
            aOutDirPath.mkdirs();
        }

        while ((zipEntry = inZip.getNextEntry()) != null) {
            szName = zipEntry.getName();

            if (zipEntry.isDirectory()) {
                // get the folder name of the widget
                szName = szName.substring(0, szName.length() - 1);
                File folder = new File(aOutDirPath, szName);
                folder.mkdirs();
            } else {
                File file = new File(aOutDirPath, szName);
                file.createNewFile();
                // get the output stream of the file
                FileOutputStream out = new FileOutputStream(file);
                int len;
                byte[] buffer = new byte[1024];
                // read (len) bytes into buffer
                while ((len = inZip.read(buffer)) != -1) {
                    // write (len) byte from buffer at the position 0
                    out.write(buffer, 0, len);
                    out.flush();
                }
                out.close();
            }
        }
        inZip.close();
    }

    /**
     * zip file or dir
     *
     * @param aSrcFile src file or dir
     * @param aZipFile out put of zip file
     * @throws Exception
     */
    public static void zip(String aSrcFile, String aZipFile) throws Exception {
        ZipOutputStream outZip = new ZipOutputStream(new FileOutputStream(aZipFile));
        File file = new File(aSrcFile);
        zipFiles(file.getParent() + File.separator, file.getName(), outZip);
        outZip.finish();
        outZip.close();
    }

    /**
     * zip files
     *
     * @param aDirPath
     * @param aFileName
     * @param aZipOutputSteam
     * @throws Exception
     */
    private static void zipFiles(String aDirPath, String aFileName, ZipOutputStream aZipOutputSteam) throws Exception {
        if (aZipOutputSteam == null) return;
        File file = new File(aDirPath + aFileName);
        if (file.isFile()) {
            ZipEntry zipEntry = new ZipEntry(aFileName);
            FileInputStream inputStream = new FileInputStream(file);
            aZipOutputSteam.putNextEntry(zipEntry);

            int len;
            byte[] buffer = new byte[4096];

            while ((len = inputStream.read(buffer)) != -1) {
                aZipOutputSteam.write(buffer, 0, len);
            }
            aZipOutputSteam.closeEntry();
        } else {
            String fileList[] = file.list();
            // if has not sub files,add to zip
            if (fileList.length <= 0) {
                ZipEntry zipEntry = new ZipEntry(aFileName + File.separator);
                aZipOutputSteam.putNextEntry(zipEntry);
                aZipOutputSteam.closeEntry();
            }

            // if has sub files
            for (int i = 0; i < fileList.length; i++) {
                zipFiles(aDirPath, aFileName + File.separator + fileList[i], aZipOutputSteam);
            }
        }
    }

}