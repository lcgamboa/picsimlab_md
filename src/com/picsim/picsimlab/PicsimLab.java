/* ########################################################################

   PICsimlab MPLABX debugger plugin

   ########################################################################

   Copyright (c) : 2015  Luis Claudio Gambôa Lopes

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 2, or (at your option)
   any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software
   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.

   For e-mail suggestions :  lcgamboa@yahoo.com
   ######################################################################## */


package com.picsim.picsimlab;

import com.microchip.mplab.comm.MPLABCommTool;
import com.microchip.mplab.mdbcore.assemblies.Assembly;
import com.microchip.mplab.mdbcore.assemblies.Party;
import com.microchip.mplab.mdbcore.assemblies.PartyException;
import com.microchip.mplab.mdbcore.common.debug.exceptions.MDBCommonToolException;
import com.microchip.mplab.mdbcore.debugger.Debugger.CONNECTION_TYPE;
import com.microchip.mplab.mdbcore.debugger.Debugger.PROGRAM_OPERATION;
import com.microchip.mplab.mdbcore.debugger.Debugger.ProjectStatusInfo;
import com.microchip.mplab.mdbcore.debugger.MDBDebugTool;
import com.microchip.mplab.util.observers.Observer;
import com.microchip.mplab.util.observers.Subject;
import java.util.Collection;

import org.openide.util.lookup.ServiceProvider;
import org.openide.util.Lookup;

import com.microchip.mplab.mdbcore.memory.PhysicalMemory;
import com.microchip.mplab.mdbcore.memory.MemoryModel;
import com.microchip.mplab.mdbcore.memory.memorytypes.*;
import com.microchip.mplab.mdbcore.memory.MemoryEvent;

import com.microchip.mplab.mdbcore.debugger.ToolEvent;

import com.microchip.mplab.mdbcore.MessageMediator.Message;
import com.microchip.mplab.mdbcore.MessageMediator.ActionList;
import com.microchip.mplab.mdbcore.MessageMediator.MessageMediator;

import com.microchip.mplab.mdbcore.ControlPointMediator.ControlPointMediator;
import com.microchip.mplab.mdbcore.ControlPointMediator.WritableControlPointStore;

import com.microchip.crownking.Pair;
import com.microchip.mplab.crownkingx.xPIC;

import java.awt.Color;

//sockets tcp
import java.io.*;
import java.net.*;



/**
 *
 * @author Luis Claudio Gambôa Lopes   lcgamboa@yahoo.com
 */
@ServiceProvider(service=MDBDebugTool.class)
public class PicsimLab implements Subject, Party, MDBDebugTool {

    // Represents the context for a debugging/programming session.  This one
    // object aggregates all other objects.
    private Assembly assembly;

    // Represents a communication channel with the physcial, hardware tool.
    private MPLABCommTool tool;
    
    private Observer obs; 
    
    private MessageMediator mm = Lookup.getDefault().lookup(MessageMediator.class);
    
    //enable debug messages
    private static final boolean DEBUG = false;
    
    //device info;
      xPIC xpic;
      int ID;
      
      //memory init, end and size
      //data
      long datai;
      long dataf;
      int datas;
      //code
      long codei;
      long codef;
      int codes;
      //configuration
      long confi;
      long conff;
      int confs;
      //id
      long idi;
      long idf;
      int ids;
      //eeprom
      long eei;
      long eef;
      int ees;
                     
    
    //connection
    Socket mSocket;
    String mServer = "127.0.0.1";
    int mServerPort = 1234;
    OutputStream mOutputStream;
    InputStream mInputStream;
    BufferedOutputStream mBufferedOutputStream; 
    
   
    //serial debuguer commands
    private static final byte STEP  =0x01;
    private static final byte RESET =0x05;
    private static final byte RUN   =0x10;
    private static final byte HALT  =0x15;
    private static final byte GETPC =0x20;
    private static final byte SETPC =0x25;
    private static final byte SETBK =0x30;
    private static final byte STRUN =0x31;
    private static final byte GETID =0x35;
    private static final byte PROGD =0x40;
    private static final byte PROGP =0x45;
    private static final byte PROGC =0x50;
    private static final byte PROGI =0x55;
    private static final byte PROGE =0x57;
    private static final byte READD =0x60;
    private static final byte READP =0x65;
    private static final byte READC =0x70;
    private static final byte READI =0x75;
    private static final byte READE =(byte)0x80;
    
    private static final byte STARTD =(byte)0xF0;
    private static final byte STOPD  =(byte)0xFF;

// Writes provided 4-byte integer to a 4 element byte array in Little-Endian order.
     public static final byte[] intToByteArray(int value) 
     {
            return new byte[] {
                (byte)(value & 0xff),
                (byte)(value >> 8 & 0xff),
                (byte)(value >> 16 & 0xff),
                (byte)(value >>> 24)
            };
     }
     
     // Writes provided 2-byte integer to a 2 element byte array in Little-Endian order.
     public static final byte[] shortToByteArray(int value) 
     {
            return new byte[] {
                (byte)(value & 0xff),
                (byte)(value >> 8 & 0xff)
                };
     }
     
     
     // Writes provided 4-byte array containing a little-endian integer to a big-endian integer.
     public static final int byteArrayToInt(byte[] value) 
     {
        int ret = ((value[0] & 0xFF) << 24) | ((value[1] & 0xFF) << 16) |
                ((value[2] & 0xFF) << 8) | (value[3] & 0xFF);
 
        return ret;
     }
     
     // Writes provided 2-byte array containing a little-endian integer to a big-endian integer.
     public static final int byteArrayToShort(byte[] value) 
     {
        int ret = ((value[1] & 0xFF) << 8) | (value[0] & 0xFF);
 
        return ret;
     }
     
    public static byte[] combine(byte[] a, byte[] b){
        int length = a.length + b.length;
        byte[] result = new byte[length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }



     
     public boolean sendcmd(byte cmd, int si, byte[] data_in, int so, byte[] data_out)
     {
        int bytesRead;
        
        if(mSocket == null)return false;
        
        if(mSocket.isClosed())return false;
        if(DEBUG)System.out.println(" sendcmd("+cmd+","+si+",data_in,"+so+",data_out)\n");
         
        // write cmd
        try {
            mBufferedOutputStream.write(cmd);
            mBufferedOutputStream.flush();
        } catch (IOException e1) {
            e1.printStackTrace();
            return false;
        }
         
        // write data _in
        if(si >= 1)
        {
          try {
              mBufferedOutputStream.write(data_in);
              mBufferedOutputStream.flush();
          } catch (IOException e1) {
            e1.printStackTrace();
            return false;
          }
        }
        
        //read data_out if so >=1
        if(so >= 1)
        {
          try {
            bytesRead = mInputStream.read(data_out,0,so);
            if(bytesRead != so)return false;
          } catch (IOException e1) {
              e1.printStackTrace();
              return false;
          }
        }
        
        
        // wait for response. 
        byte[] responseBytes = new byte[1];

        try {
          bytesRead = mInputStream.read(responseBytes,0,1);
        } catch (IOException e1) {
            e1.printStackTrace();
            return false;
        }
        
        if (bytesRead != 1) {
         // communication error. Abort.
         return false;
        }
 
        if (responseBytes[0] != 0x00) {
            return false; 
        }
        
        
        return true;
     }
     
     
     
    //////////////////////////////////////////////////////////////////
    //
    // Implementation of 'Subject'
    //
    //////////////////////////////////////////////////////////////////

    @Override
    public boolean Attach(Observer observer, Object obj) {
        // Adds an Observer to this Subject.
        //
        // The subject/observer pattern (GoF 293) enables many Observers to
        // receive notification when something interesting happens in some
        // Subject.  An Observer expresses interest in a Subject's events by
        // calling this method.  The Subject then calls the Observer back, via
        // Observer.Update(), whenever the event occurs.
        //
        // There is a nice implementation of the Subject interface in
        // com.microchip.mplab.util.observers.Observable.  That implementation
        // simply tracks the Observers in a List<Observer> and calls
        // Observer.Update() whenever Subject.Notify() is called.
        // 
        // We use this pattern in many different places to inform upper-level
        // software when something has happened.  An MDBDebugTool must Notify
        // its Observers of the following conditions:
        //
        //  * We noticed that the target device halted.
        //
        //  * We successfully issued the run command (at the end of RunTarget)
        //
        //  * We finished reading memory (at the end of ReadTargetMemory)

        if(mSocket == null)
        {
        try {
            // set up connection with server
            this.mSocket = new Socket(mServer, mServerPort);
        } catch (Exception ee) {
            return false;
        }
 
        // get the I/O streams for the socket.
        try {
            mOutputStream = this.mSocket.getOutputStream();
            mBufferedOutputStream = new BufferedOutputStream(mOutputStream);
            mInputStream = this.mSocket.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        }
           
         
        obs=observer;

        if(DEBUG)System.out.println("PicsimLab.Attach() was called");
               
        return sendcmd(STARTD,0,null,0,null);
        
    }
    

        

    @Override
    public boolean Detach(Observer observer, Object obj) {
        // Removes an Observer from this Subject---the observer is no longer
        // interested in events.
        
        sendcmd(STOPD,0,null,0,null);

       try {
         mInputStream.close();
         mBufferedOutputStream.close();
         mOutputStream.close();
         this.mSocket.close();
       }catch (Exception ee) {
            return false;
       }
        
        obs=null;

        if(DEBUG)System.out.println("PicsimLab.Detach() was called");
        return true;
    }

    @Override
    public void Notify(Object obj) {
        // Some change has occurred that is interesting to Observers.  While
        // this is a public interface method, it's most common for an
        // MDBDebugTools to call it internally.  (See comments in Attach.)

        if(DEBUG)System.out.println("PicsimLab.Notify() was called");
    }

    //////////////////////////////////////////////////////////////////
    //
    // Implementation of 'Party'
    //
    //////////////////////////////////////////////////////////////////

    @Override
    public void Engage(Assembly assembly) throws PartyException {
        // Informs this tool of its host Assembly.  This is the very first
        // method called after an instance of this tool constructed.  (You will
        // want to save this Assembly for later.)
        //
        // Any methods called between 'Engage()' and 'Dismiss()' may assume
        // that they are acting upon the Assembly passed to 'Engage()'.

        if(DEBUG)System.out.println("PicsimLab.Engage() was called");
        this.assembly = assembly;
    }

    @Override
    public void Dismiss() {
        // This tool has been removed from the Party.  This is a good place to
        // close communication with channel assigned through SetHWTool.  This
        // is also a good place to free/release any objects associated with
        // this debug session that that may be resistant to garbage collection.

        if(DEBUG)System.out.println("PicsimLab.Dismiss() was called");
        this.assembly = null;
    }

    //////////////////////////////////////////////////////////////////
    //
    // Implementation of 'MDBDebugTool'
    //
    //////////////////////////////////////////////////////////////////

    @Override
    public void SetHWTool(MPLABCommTool Tool) {
        // Assigns a physical communication channel to this 'tool' so it can
        // talk to the physical, hardware tool.  (You will want to save this
        // for later.)
        //
        // This may be a good time for you to query the the USB descriptor for
        // data that would help you talk to the tool more effectively.
        // However, this can be delayed until the ConnectToTool method is
        // called.
        if(mSocket == null)
        {
        try {
            // set up connection with server
            this.mSocket = new Socket(mServer, mServerPort);
        } catch (Exception ee) {
        }
 
        // get the I/O streams for the socket.
        try {
            mOutputStream = this.mSocket.getOutputStream();
            mBufferedOutputStream = new BufferedOutputStream(mOutputStream);
            mInputStream = this.mSocket.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        }
    
        
        if(DEBUG)System.out.println("PicsimLab.SetHWTool() was called");
        this.tool = Tool;
    }

 
    @Override
    public boolean ConnectToTool(CONNECTION_TYPE type) throws MDBCommonToolException {
        // Prepares the tool for either debugging or programming.  (A separate
        // method call to do one of those things will likely follow.)
        // Typically, this is where we check the state of the debug tool's
        // firmware.  Some debug tools support firmware updates.  Now is the
        // time to check and see if a firmware update is needed.  Also if a
        // debug exec is present, this would be a good time to program it.  Or,
        // we could delay until the ProgramTarget method is called.
     
        if(mSocket == null)
        {
        try {
            // set up connection with server
            this.mSocket = new Socket(mServer, mServerPort);
        } catch (Exception ee) {
            return false;
        }
 
        // get the I/O streams for the socket.
        try {
            mOutputStream = this.mSocket.getOutputStream();
            mBufferedOutputStream = new BufferedOutputStream(mOutputStream);
            mInputStream = this.mSocket.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        }
              
        if(DEBUG)System.out.println("PicsimLab.ConntectToTool() was called");
        
        obs.Update(ToolEvent.EVENTS.CONNECTED);
        return true;
    }

    @Override
    public boolean ProgramTarget(PROGRAM_OPERATION op) throws MDBCommonToolException {
        // Programs the content of the Assembly's memory objects to the target
        // device.  The PROGRAM_OPERATION can be AUTO_SELECT (which means
        // 'normal' programming) or it can be PROGRAMMER_TO_GO.  The latter is
        // applicable only to debug tools that can receive a memory image for
        // programming at a laater time.  Later, the user may tell this debug
        // tool (while NOT connected to MPLAB X) to program this image to some
        // device.  (Microchip's PICkit 3 has this capability.)
        
        obs.Update(ToolEvent.EVENTS.PROGRAM_START);
        if(DEBUG)System.out.println("PicsimLab.ProgramTarget() was called");
    
            
        xpic = assembly.GetDevice();
        
            
        Pair<Long, Long> range;
           
        ID = (int)xpic.getMainPartition().getDeviceIDValue();
        if(DEBUG)System.out.println("PIC ID="+ID+"\n");
        
        byte idb[] = new byte[2];
        if(! sendcmd(GETID,0,null,2,idb))
        {
          mm.handleMessage(new Message("Communication error!\n", "picsim", Color.red,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
          tool.Disconnect();
          return false;
        }    
       
        if(ID != byteArrayToShort(idb))
        {
            mm.handleMessage(new Message("Pic ID don't match!!!!!!:"+String.format("%#06X", ID)+"!="+String.format("%#06X", byteArrayToShort(idb))+" \n", "picsim", Color.red,  true,  true, true),  ActionList.OutputWindowOnlyDisplayColor);  
           return false;
        }
        
        range = xpic.getMainPartition().getFileRange();
        datai = range.first;
        dataf   = range.second;
        datas=(int)(dataf-datai);
        if(DEBUG)System.out.println("mem start="+datai+"\n stop="+dataf+"\n");

        
        
        range = xpic.getMainPartition().getInstRange();
        codei = range.first;
        codef   = range.second;
        codes=(int)(codef-codei);
        if(DEBUG)System.out.println("code start="+codei+"\n stop="+codef+"\n");

     
        
        range = xpic.getMainPartition().getDCRRange();
        confi = range.first;
        conff   = range.second;
        confs=(int)(conff-confi);
        if(DEBUG)System.out.println("conf start="+confi+"\n stop="+conff+"\n");

        
        range = xpic.getMainPartition().getDeviceIDRange();
        idi = range.first;
        idf   = range.second;
        ids=(int)(idf-idi);
        if(DEBUG)System.out.println("ID start="+idi+"\n stop="+idf+"\n");
         
        range = xpic.getMainPartition().getEEDataRange();
        eei = range.first;
        eef   = range.second;
        ees=(int)(eef-eei);
        if(DEBUG)System.out.println("eeprom start="+eei+"\n stop="+eef+"\n");
   
 
        
        PhysicalMemory PMem;
        long r;
        long AddrInc; 
        long WordSize;
        
        MemoryModel Memp = (MemoryModel)assembly.getLookup().lookup(ProgramMemory.class);   
        AddrInc    = Memp.WordIncrement();
        WordSize  =  Memp.WordSize();
        codes = (int)(((codes) / AddrInc) * WordSize);
        
        byte Datap[] = new byte[codes];
        PMem = Memp.GetPhysicalMemory();
        r=PMem.Read(codei, codes, Datap);
        if(!sendcmd(PROGP,codes,Datap,0,null))
        {
          mm.handleMessage(new Message("Communication error!\n", "picsim", Color.red,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
          tool.Disconnect();
          return false;
        }    
        
        mm.handleMessage(new Message("Program Target Write ProgMem:"+r+" of "+codes+ "\n", "picsim", Color.black,  true,  true, true),  ActionList.OutputWindowOnlyDisplayColor);  
        
    
        
        MemoryModel Memc = (MemoryModel)assembly.getLookup().lookup(ConfigurationBits.class);  
        AddrInc   = Memc.WordIncrement();
        WordSize  = Memc.WordSize();
        confs = (int)(((confs) / AddrInc) * WordSize);
        byte Datac[] = new byte[confs];
        PMem = Memc.GetPhysicalMemory();
        r=PMem.Read(confi, confs, Datac);
        if(!sendcmd(PROGC,confs,Datac,0,null))
        {
          mm.handleMessage(new Message("Communication error!\n", "picsim", Color.red,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
          tool.Disconnect();
          return false;
        }    
        
         mm.handleMessage(new Message("Program Target Write ConfMem:"+r+" of "+confs+ "\n", "picsim", Color.black,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
  
/*         
//  TODO user ID
        MemoryModel Memi = (MemoryModel)assembly.getLookup().lookup(UserID.class);      
        AddrInc    = Memi.WordIncrement();
        WordSize  =  Memi.WordSize();
        ids = (int)(((ids) / AddrInc) * WordSize);
        byte Datai[] = new byte[ids];
        PMem = Memi.GetPhysicalMemory();
        r=PMem.Read(0, ids, Datai);

        if(!sendcmd(PROGI,ids,Datai,0,null))
        {
            mm.handleMessage(new Message("Communication error!\n", "picsim", Color.red,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
            tool.Disconnect();
            return false;
        }
        
        mm.handleMessage(new Message("Program Target Write IDMem:"+r+" of "+ids+ "\n", "picsim", Color.black,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
*/
         

        if(ees >0)
        {
          MemoryModel Meme = (MemoryModel)assembly.getLookup().lookup(EEData.class);      
          AddrInc    = Meme.WordIncrement();
          WordSize  =  Meme.WordSize();
          ees = (int)(((ees) / AddrInc) * WordSize);
          byte Datae[] = new byte[ees];
          PMem = Meme.GetPhysicalMemory();
          r=PMem.Read(0, ees, Datae);
        
          if(!sendcmd(PROGE,ees,Datae,0,null))
          {
            mm.handleMessage(new Message("Communication error!\n", "picsim", Color.red,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
            tool.Disconnect();
            return false;
          }
        
          mm.handleMessage(new Message("Program Target Write EEprom:"+r+" of "+ees+ "\n", "picsim", Color.black,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
        }
         
        obs.Update(ToolEvent.EVENTS.PROGRAM_DONE);
        
        if(!sendcmd(RESET,0,null,0,null))
        {
          mm.handleMessage(new Message("Communication error!\n", "picsim", Color.red,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
          tool.Disconnect();
          return false;
        }    
        
        return HaltTarget();
        
    }

    @Override
    public void HoldTargetInReset() throws MDBCommonToolException {
        // Directs the tool to hold the nMCLR line in reset in its
        // ProgramTarget implementation.

        if(DEBUG)System.out.println("PicsimLab.HoldTargetInReset() was called");
    }

    @Override
    public void ReleaseTargetFromReset() throws MDBCommonToolException {
        // Directs the tool to release the nMCLR line in its ProgramTarget
        // implementation.

        if(DEBUG)System.out.println("PicsimLab.ReleaseTargetFromReset() was called");
    }

    @Override
    public boolean ReadTarget() throws MDBCommonToolException {
        // Reads all memory on the target device and updates the Assembly's
        // memory objects accordingly.

        if(DEBUG)System.out.println("PicsimLab.ReadTarget() was called");
        
        return true;
    }

    @Override
    public boolean RunTarget() throws MDBCommonToolException {
        // Runs the user program on the target device.

        //send ram memory 
        PhysicalMemory PMem;
        long r;
        MemoryModel Memd = (MemoryModel)assembly.getLookup().lookup(FileRegisters.class);      
        byte Datad[] = new byte[datas];
        PMem = Memd.GetPhysicalMemory();
        r=PMem.Read(datai, datas, Datad);
        if(!sendcmd(PROGD,datas,Datad,0,null))
        {
            mm.handleMessage(new Message("Communication error!\n", "picsim", Color.red,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
            tool.Disconnect();
            return false;
        }
        
       //send breakpoints          
        ControlPointMediator cpm = assembly.getLookup().lookup(ControlPointMediator.class);
        
        WritableControlPointStore WCPS=cpm.getWritableControlPointStore();
        
        int bpc = WCPS.getNumberControlPoints();
          
        byte bpd[]=shortToByteArray(bpc);
      
        
        for(int x=0;x< bpc;x++)
        {
          long addr=WCPS.getWritableControlPoint(x).getBreakAddress();
          bpd=combine(bpd,shortToByteArray((int)addr));            
        }    
        
        cpm.releaseWritableControlPointStore(WCPS);
        
        
        if(!sendcmd(SETBK,2+bpc*2,bpd,0,null))
        {
          mm.handleMessage(new Message("Communication error!\n", "picsim", Color.red,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
          tool.Disconnect();
          return false;
        }
        
        //send run command
        if(!sendcmd(RUN,0,null,0,null))
        {
          mm.handleMessage(new Message("Communication error!\n", "picsim", Color.red,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
          tool.Disconnect();
          return false;
        }    
      
        mm.handleMessage(new Message("Run Target \n", "picsim", Color.black,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
        if(DEBUG)System.out.println("PicsimLab.RunTarget() was called");
        
        obs.Update(ToolEvent.EVENTS.RUN); 
        
        
        
        if(bpc > 0)//If breakpoints exists, wait for them
        {    
          byte st[]=new byte[1];
          if(!sendcmd(STRUN,0,null,1,st))
          {
            mm.handleMessage(new Message("Communication error!\n", "picsim", Color.red,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
            tool.Disconnect();
            return false;
          }
                
          while(st[0] == 0){
            try {
               Thread.sleep(500);              
             } catch(InterruptedException ex) {
              Thread.currentThread().interrupt();
             } 
           if(!sendcmd(STRUN,0,null,1,st))
           {
              mm.handleMessage(new Message("Communication error!\n", "picsim", Color.red,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
              tool.Disconnect();
              return false;
            }
          }
          mm.handleMessage(new Message("Breakpoint \n", "picsim", Color.black,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
          HaltTarget();
        }
        
      
        return true;
    }

    @Override
    public boolean SingleStepTarget() throws MDBCommonToolException {
        // Issues a single step command to the debug tool.
        
 
        if(!sendcmd(STEP,0,null,0,null))
        {
          mm.handleMessage(new Message("Communication error!\n", "picsim", Color.red,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
          tool.Disconnect();
          return false;
        }    

        mm.handleMessage(new Message("Single Step \n", "picsim", Color.black,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
        if(DEBUG)System.out.println("PicsimLab.SingleStepTarget() was called");
        return true;
    }

    @Override
    public boolean BeginFastOp() throws MDBCommonToolException {
        // Warns the debug tool that we are about to step a complex statement
        // of high-level language code so the debug tool can optimize the
        // intermediary (assembly language) steps that the user cannot see.
        //
        // This optimization is beneficial when (1) taking a single step is
        // very expensive, and (2) the stepping overhead doesn't need to happen
        // before/after each of the intermediary steps, but can happen
        // before/after the entire group of steps.
        //
        // For example, if stepping requires saving 10 registers, stepping and
        // then putting the 10 registers back, and if we are about to single
        // step 10 times to get to the next high level language line, we could
        // do the following 10 times during a call to the SingleStepTarget:
        //
        //     save 10 registers
        //     step 
        //     restore 10 registers
        //
        // ...or we could:
        //
        //     save 10 registers (BeginFastOp is called)
        //     step 10 times  (FastStep is called)
        //     restore 10 registers (EndFastOp is called)
        //
        // The nature of the tool determines whether this optimization has
        // merit.  If the optimization has no merit, then the calls to
        // BeginFastOp and EndFastOp should do nothing and the call to FastStep
        // should simply call SingleStepTarget.
        
        PhysicalMemory PMem;
        long r;
        MemoryModel Memd = (MemoryModel)assembly.getLookup().lookup(FileRegisters.class);      
        byte Datad[] = new byte[datas];
        PMem = Memd.GetPhysicalMemory();
        r=PMem.Read(datai, datas, Datad);
        if(!sendcmd(PROGD,datas,Datad,0,null))
        {
            mm.handleMessage(new Message("Communication error!\n", "picsim", Color.red,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
            tool.Disconnect();
            return false;
        }

        if(DEBUG)System.out.println("PicsimLab.BeginFastOp() was called");
        return true;
    }

    @Override
    public boolean FastStep() throws MDBCommonToolException {
        // (See comments in BeginFastOp.)

        if(DEBUG)System.out.println("PicsimLab.FastStep() was called");
        SingleStepTarget();
        return true;
    }

    @Override
    public boolean EndFastOp() throws MDBCommonToolException {
        // (See comments in BeginFastOp.)
        
        PhysicalMemory PMem;
        long r;
        MemoryModel Memd = (MemoryModel)assembly.getLookup().lookup(FileRegisters.class);      
        byte Datad[] = new byte[datas];
        PMem = Memd.GetPhysicalMemory();
        if(!sendcmd(READD,0,null,datas,Datad))
        {
          mm.handleMessage(new Message("Communication error!\n", "picsim", Color.red,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
          tool.Disconnect();
          return false;
        }    
        r=PMem.Write(datai, datas, Datad);
        Memd.GetPhysicalMemory().Notify(new MemoryEvent(MemoryEvent.EVENTS.MEMORY_CHANGED) );
        
        if(DEBUG)System.out.println("PicsimLab.EndFastOp() was called");
        return true;
    }

    @Override
    public boolean VerifyTarget() throws MDBCommonToolException {
        // Returns 'true' if the content of the Assembly's memory objects match
        // the content of the physical device memory, returns 'false' if they
        // do not, and throws an exception if unable to verify.

        if(DEBUG)System.out.println("PicsimLab.VerifyTarget() was called");
        return true;
    }

    @Override
    public boolean EraseTarget() throws MDBCommonToolException {
        // Erases all memory on the target device.

        if(DEBUG)System.out.println("PicsimLab.EraseTarget() was called");
        return true;
    }

    @Override
    public boolean BlankCheckTarget() throws MDBCommonToolException {
        // Returns 'true' if the physical device memory is blank, returns
        // 'false' if it is not, and throws an exception if unable to perform
        // the blank check.

        if(DEBUG)System.out.println("PicsimLab.BlankCheckTarget() was called");
        return true;
    }

    @Override
    public boolean HaltTarget() throws MDBCommonToolException {
        // Puts the target device into debug mode, permitting access to
        // SFRs/GPRs and stepping.  This method can be called while the target
        // device is running the user program.

        if(!sendcmd(HALT,0,null,0,null))
        {
          mm.handleMessage(new Message("Communication error!\n", "picsim", Color.red,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
          tool.Disconnect();
          return false;
        }    
        
        PhysicalMemory PMem;
        long r;
        MemoryModel Memd = (MemoryModel)assembly.getLookup().lookup(FileRegisters.class);      
        byte Datad[] = new byte[datas];
        PMem = Memd.GetPhysicalMemory();
        if(!sendcmd(READD,0,null,datas,Datad))
        {
          mm.handleMessage(new Message("Communication error!\n", "picsim", Color.red,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
          tool.Disconnect();
          return false;
        }    
        r=PMem.Write(datai, datas, Datad);
        Memd.GetPhysicalMemory().Notify(new MemoryEvent(MemoryEvent.EVENTS.MEMORY_CHANGED) );
     
        mm.handleMessage(new Message("Halt Target \n", "picsim", Color.black,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
        if(DEBUG)System.out.println("PicsimLab.HaltTarget() was called");
        
        obs.Update(ToolEvent.EVENTS.HALT);
        return true;
    }

    @Override
    public boolean ResetTarget() throws MDBCommonToolException {
        // Resets the target device.  This may be more complex than toggling
        // the nMCLR line since a debug executive may may be present on the
        // target device.  This method is called during a debug session only.
        
        
        
        if(!sendcmd(RESET,0,null,0,null))
        {
          mm.handleMessage(new Message("Communication error!\n", "picsim", Color.red,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
          tool.Disconnect();
          return false;
        }    
        
        PhysicalMemory PMem;
        long r;
        MemoryModel Memd = (MemoryModel)assembly.getLookup().lookup(FileRegisters.class);      
        byte Datad[] = new byte[datas];
        PMem = Memd.GetPhysicalMemory();
        if(!sendcmd(READD,0,null,datas,Datad))
        {
          mm.handleMessage(new Message("Communication error!\n", "picsim", Color.red,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
          tool.Disconnect();
          return false;
        }    
        r=PMem.Write(datai, datas, Datad);
        Memd.GetPhysicalMemory().Notify(new MemoryEvent(MemoryEvent.EVENTS.MEMORY_CHANGED) );
         
        mm.handleMessage(new Message("Reset Target \n", "picsim", Color.black,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
        if(DEBUG)System.out.println("PicsimLab.ResetTarget() was called");
        
        obs.Update(ToolEvent.EVENTS.RESET);
        return true;
    }

    @Override
    public void SetPC(long address) throws MDBCommonToolException {
        // Assigns a program-counter value to the target device.  This method
        // is called during a debug session only.

        if(!sendcmd(SETPC,2,shortToByteArray((int)address),0,null))
        {
            mm.handleMessage(new Message("Communication error!\n", "picsim", Color.red,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
            tool.Disconnect();
            return;
          }
        
        if(DEBUG)System.out.println("PicsimLab.SetPC() was called");
    }

    @Override
    public long ReadTargetMemory(MEMTYPE memtype, long address, long size, byte[] data) throws MDBCommonToolException {
        // Reads data from the target device and and updates the corresponding
        // memory objects in the Assembly.  This method is called during a
        // debug session only.

        if(DEBUG)System.out.println("PicsimLab.ReadTargetMemory() was called");
        return 0L;
    }

    @Override
    public long WriteTargetMemory(MEMTYPE memtype, long address, long size, byte[] data) throws MDBCommonToolException {
        // Reads data from the Assembly's memory objects and writes it to the
        // target device.  Typically, we do not write to program memory with
        // this method---for that we typically use the 'ProgramTarget()'
        // method.  This method is called during a debug session only.

        if(DEBUG)System.out.println("PicsimLab.WriteTargetMemory() was called");
        return 0L;
    }

    @Override
    public long GetPC() throws MDBCommonToolException {
        // Returns the current program-counter value.  This method is called
        // during a debug session only.

        byte pcb[] = new byte[2];
        if(!sendcmd(GETPC,0,null,2,pcb))
        {
          mm.handleMessage(new Message("Communication error!\n", "picsim", Color.red,  false,  true, false),  ActionList.OutputWindowOnlyDisplayColor);  
          tool.Disconnect();
          return 0;
        }        
        
        if(DEBUG)System.out.println("PicsimLab.GetPC() was called");
        
        return byteArrayToShort(pcb);
    }

    @Override
    public long GetPreviousPC() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void Abort() throws MDBCommonToolException {
        // Does whatever must be done to stop the current debug session.

        if(DEBUG)System.out.println("PicsimLab.Abort() was called");
    }

    @Override
    public Long GetStopwatchValue() {
        // Returns the value of the 'stopwatch'.  Obviously, this method makes
        // sense only if the tool has stopwatch capability.

        if(DEBUG)System.out.println("PicsimLab.GetStopwatchValue() was called");
        return null;
    }

    @Override
    public boolean SupportsDebugModeRead() {
        // Returns 'true' if the tool can, while halted, read the entire target
        // device memory back into the Assembly's memory objects WITHOUT
        // disturbing the the debug session.  Returns 'false' if the tool
        // cannot do this.

        if(DEBUG)System.out.println("PicsimLab.SupportsDebugModeRead() was called");
        return true;
    }

    @Override
    public Collection<ProjectStatusInfo> GetProjectStatusInfo() {
        // Returns detailed information about the current state of the debug
        // session.

        if(DEBUG)System.out.println("PicsimLab.GetProjectStatusInfo() was called");
        return null;
    }

    @Override
    public void TestTool() {
        if(DEBUG)System.out.println("PicsimLab.TestTool() was called");
        // Starts a 'self-test' procedure if the tool has self-test capability.
    }

}
