# Dataverse backup, ssh io module

import sys
import io
import paramiko
from config import (ConfigSectionMap)

my_ssh_client = None

def open_ssh_client():
    ssh_host = ConfigSectionMap("Backup")['sshhost']
    ssh_port = ConfigSectionMap("Backup")['sshport']
    ssh_username = ConfigSectionMap("Backup")['sshusername']

    print "SSH Host: %s" % (ssh_host)
    print "SSH Port: %s" % (ssh_port)
    print "SSH Username: %s" % (ssh_username)


    ssh_client=paramiko.SSHClient()
    ssh_client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh_client.connect(hostname=ssh_host,username=ssh_username)

    print "Connected!"

    return ssh_client

def transfer_file(local_file, dataset_authority, dataset_identifier, storage_identifier):
    sftp_client=my_ssh_client.open_sftp()

    remote_dir = dataset_authority + "/" + dataset_identifier

    subdirs = remote_dir.split("/")

    cdir = ""
    for subdir in subdirs:
        try:
            cdir = cdir + subdir + "/"
            sftpattr=sftp_client.stat(cdir)
        except IOError:
            print "directory "+cdir+" does not exist (creating)"
            sftp_client.mkdir(cdir)
        else:
            print "directory "+cdir+" already exists"

    remote_file = remote_dir  + "/" + storage_identifier
    sftp_client.put(local_file,remote_file)
    sftp_client.close()

    print "File transfered."

    return remote_file

def verify_remote_file(remote_file, checksum_type, checksum_value):
    try: 
        stdin,stdout,stderr=my_ssh_client.exec_command("ls "+remote_file)
        remote_file_checked = stdout.readlines()[0].rstrip("\n\r")
    except:
        raise ValueError("remote file check failed (" + remote_file + ")")

    if (remote_file != remote_file_checked):
        raise ValueError("remote file NOT FOUND! (" + remote_file_checked + ")")

    if (checksum_type == "MD5"):
        remote_command = "md5sum"
    elif (checksum_type == "SHA1"):
        remote_command = "sha1sum"
        
    try: 
        stdin,stdout,stderr=my_ssh_client.exec_command(remote_command+" "+remote_file)
        remote_checksum_value = (stdout.readlines()[0]).split(" ")[0]
    except: 
        raise ValueError("remote checksum check failed (" + remote_file + ")")

    if (checksum_value != remote_checksum_value):
        raise ValueError("remote checksum BAD! (" + remote_checksum_value + ")")


def backup_file_ssh(file_input, dataset_authority, dataset_identifier, storage_identifier, checksum_type, checksum_value):
    global my_ssh_client
    if (my_ssh_client is None):
        my_ssh_client = open_ssh_client()
        print "ssh client is not defined"

    file_transfered = transfer_file(file_input, dataset_authority, dataset_identifier, storage_identifier)

    verify_remote_file(file_transfered, checksum_type, checksum_value)

def main():

    print "entering ssh (standalone mode)"


    try:
        backup_file_ssh("config.ini", "1902.1", "XYZ", "config.ini", "MD5", "8e6995806b1cf27df47c5900869fdd27")
    except ValueError:
        print "failed to verify file (\"config.ini\")"
    else:
        print "file ok"


if __name__ == "__main__":
    main()


