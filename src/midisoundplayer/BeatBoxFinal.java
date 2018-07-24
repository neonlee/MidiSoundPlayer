package midisoundplayer;

import java.awt.*;
import javax.swing.*;
import java.io.*;
import javax.sound.midi.*;
import java.util.*;
import java.awt.event.*;
import java.net.*;
import javax.swing.event.*;

public class BeatBoxFinal {

    JFrame theFrame;
    JPanel mainPanel;
    JList incomingList;
    JTextField userMessage;
    ArrayList<JCheckBox> checkBoxList;
    int nextNum;
    Vector<String> listVector = new Vector<>();
    String userName;
    ObjectOutputStream out;
    ObjectInputStream in;
    HashMap<String, boolean[]> otherSeqMap = new HashMap<>();

    Sequencer sequencer;
    Sequence sequence;
    Sequence mySequence = null;
    Track track;

    String[] instrumentsNames = {"Bass drum", "Closet hi-hat", "Open hi-hat", "Acustic snare", "Crash cymbal", "Hand clap", "High tom",
        "Hi bongo", "Maracas", "Whistle", "Low conga", "Cowbell", "Vibraslap", "Low-midi tom", "Hihi agogo", "Open hi conga"};

    int[] instruments = {35, 42, 46, 38, 49, 39, 50, 60, 70, 72, 64, 56, 58, 47, 67, 63};

    public static void main(String[] args) {
        new BeatBoxFinal().startUp("");
    }

    public void startUp(String name) {
        userName = name;

        try {
            Socket sock = new Socket("127.0.0.1", 4242);
            out = new ObjectOutputStream(sock.getOutputStream());
            in = new ObjectInputStream(sock.getInputStream());
            Thread remote = new Thread(new RemoteReader());
            remote.start();
        } catch (IOException e) {
            System.out.println("Couldn't connect");
        }
        setUpMidi();
        buildGUI();
    }

    private void buildGUI() {
        theFrame = new JFrame("Cyber Beat Box");
        BorderLayout layout = new BorderLayout();
        JPanel background = new JPanel(layout);
        background.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        checkBoxList = new ArrayList<>();

        Box buttonBox = new Box(BoxLayout.Y_AXIS);
        JButton start = new JButton("Start");
        start.addActionListener(new myStartListener());
        buttonBox.add(start);

        JButton stop = new JButton("Stop");
        stop.addActionListener(new myStopListener());
        buttonBox.add(stop);

        JButton upTempo = new JButton("Tempo up");
        upTempo.addActionListener(new myUpTempoListener());
        buttonBox.add(upTempo);

        JButton downTempo = new JButton("Down tempo");
        downTempo.addActionListener(new myDownTempoListener());
        buttonBox.add(downTempo);

        JButton sendIt = new JButton("Send it");
        sendIt.addActionListener(new mySendListener());
        buttonBox.add(sendIt);

        userMessage = new JTextField();
        buttonBox.add(userMessage);

        incomingList = new JList();
        incomingList.addListSelectionListener(new myListSelectionListener());
        incomingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane theList = new JScrollPane(incomingList);
        buttonBox.add(theList);
        incomingList.setListData(listVector);

        Box nameBox = new Box(BoxLayout.Y_AXIS);
        for (int i = 0; i < 16; i++) {
            nameBox.add(new Label(instrumentsNames[i]));
        }

        background.add(BorderLayout.EAST, buttonBox);
        background.add(BorderLayout.WEST, nameBox);
        theFrame.getContentPane().add(background);
        GridLayout grid = new GridLayout(16, 16);
        grid.setVgap(1);
        grid.setVgap(2);
        mainPanel = new JPanel(grid);
        background.add(BorderLayout.CENTER, mainPanel);

        for (int i = 0; i < 256; i++) {
            JCheckBox c = new JCheckBox();
            c.setSelected(false);
            checkBoxList.add(c);
            mainPanel.add(c);
        }

        theFrame.setBounds(50, 50, 300, 300);
        theFrame.pack();
        theFrame.setVisible(true);
    }

    private void setUpMidi() {
        try {
            sequencer = MidiSystem.getSequencer();
            sequencer.open();

            sequence = new Sequence(Sequence.PPQ, 4);
            track = sequence.createTrack();
            sequencer.setTempoInBPM(120);
        } catch (InvalidMidiDataException | MidiUnavailableException e) {
        }

    }

    public void buildTrackAndStart() {
        ArrayList<Integer> trackList = null;
        sequence.deleteTrack(track);
        track = sequence.createTrack();

        for (int i = 0; i < 16; i++) {

            trackList = new ArrayList<>();

            for (int j = 0; j < 16; j++) {
                JCheckBox jc = checkBoxList.get(j + (16 * i));

                if (jc.isSelected()) {
                    int key = instruments[i];
                    trackList.add(key);
                } else {
                    trackList.add(null);
                }
            }
            makeTrack(trackList);
        }
        track.add(makeEvent(192, 9, 1, 0, 15));

        try {
            sequencer.setSequence(sequence);
            sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
            sequencer.start();
            sequencer.setTempoInBPM(120);
        } catch (InvalidMidiDataException e) {
            e.printStackTrace();
        }

    }

    private class RemoteReader implements Runnable {

        boolean[] checkBoxState = null;
        String nomeToShow = null;
        Object obj = null;

        @Override
        public void run() {
            try {
                while ((obj = in.readObject()) != null) {
                    System.out.println("got an object from server");
                    System.out.println(obj.getClass());

                    nomeToShow = (String) obj;

                    checkBoxState = (boolean[]) in.readObject();

                    otherSeqMap.put(nomeToShow, checkBoxState);
                    listVector.add(nomeToShow);
                    incomingList.setListData(listVector);
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

    }

    private class myListSelectionListener implements ListSelectionListener {

        @Override
        public void valueChanged(ListSelectionEvent lse) {
            if (lse.getValueIsAdjusting()) {
                String selected = (String) incomingList.getSelectedValue();

                if (selected != null) {
                    boolean[] selectedState = otherSeqMap.get(selected);
                    changeSequence(selectedState);
                    sequencer.stop();
                    buildTrackAndStart();
                }
            }
        }

    }

    private class mySendListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent ae) {
            boolean[] checkBoxState = new boolean[256];

            for (int i = 0; i < 256; i++) {
                JCheckBox check = checkBoxList.get(i);

                if (check.isSelected()) {
                    checkBoxState[i] = true;
                }
            }

            String messageToSend = null;

            try {
                out.writeObject(userName + nextNum++ + ":" + userMessage.getText());
                out.writeObject(checkBoxState);
            } catch (IOException e) {
                System.out.println("Sorry dude. couldn't send it to the server");
            }
            userMessage.setText("");
        }
    }

    private class myUpTempoListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent ae) {
            float tempoFactory = sequencer.getTempoFactor();
            sequencer.setTempoFactor((float) (tempoFactory * 1.03));
        }
    }

    private class myDownTempoListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent ae) {
            float tempoFactory = sequencer.getTempoFactor();
            sequencer.setTempoFactor((float) (tempoFactory * .97));
        }
    }

    private class myStopListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent ae) {
            sequencer.stop();
        }
    }

    private class myStartListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent ae) {
            buildTrackAndStart();
        }
    }

    public class myPlayMineListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent ae) {
            if (mySequence != null) {
                sequence = mySequence;
            }
        }

    }

    private void makeTrack(ArrayList<Integer> trackList) {
        Iterator it = trackList.iterator();

        for (int i = 0; i < 16; i++) {
            Integer num = (Integer) it.next();
            if (num != null) {
                int numKey = num.intValue();
                track.add(makeEvent(144, 9, numKey, 100, i));
                track.add(makeEvent(128, 9, numKey, 100, i + 1));
            }
        }
    }

    private MidiEvent makeEvent(int i, int i0, int i1, int i2, int i3) {
        MidiEvent event = null;

        try {
            ShortMessage a = new ShortMessage();
            a.setMessage(i, i0, i1, i2);
            event = new MidiEvent(a, i3);
        } catch (InvalidMidiDataException e) {
        }
        
        return event;
    }

    private void changeSequence(boolean[] selectedState) {
        for (int i = 0; i < 256; i++) {
            JCheckBox check = checkBoxList.get(i);

            if (selectedState[i]) {
                check.setSelected(true);
            } else {
                check.setSelected(false);
            }
        }
    }
}
