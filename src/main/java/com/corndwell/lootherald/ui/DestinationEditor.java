package com.corndwell.lootherald.ui;

import com.corndwell.lootherald.discord.Destination;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/** Edits the secret multiline setting through masked individual fields. Never contacts Discord. */
public final class DestinationEditor
{
    private static final int ROWS = 5;
    private static final Color BACKGROUND = new Color(29, 31, 35);
    private static final Color FIELD = new Color(48, 52, 59);
    private static final Color TEXT = new Color(231, 234, 239);
    private static final Color MUTED = new Color(168, 175, 187);
    private static final Color GOLD = new Color(239, 190, 98);
    private static final Color ERROR = new Color(242, 142, 139);
    private static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    private DestinationEditor() {}

    /** The save callback is invoked on the EDT only after every row validates. Cancel is a no-op. */
    public static void show(Component parent, String currentValue, Consumer<String> onSave)
    {
        Objects.requireNonNull(onSave);
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(() -> show(parent, currentValue, onSave));
            return;
        }
        JDialog dialog = new JDialog(parent == null ? null : SwingUtilities.getWindowAncestor(parent),
            "Discord destinations", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel frame = new JPanel(new BorderLayout(0, 14));
        frame.setBackground(BACKGROUND);
        frame.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JPanel heading = new JPanel(new BorderLayout(0, 7));
        heading.setOpaque(false);
        JLabel title = label("Discord destinations", GOLD);
        title.setFont(BODY.deriveFont(Font.BOLD, 19f));
        heading.add(title, BorderLayout.NORTH);
        heading.add(note("Add up to five named webhooks. URLs stay masked. Leave both fields blank to remove a destination; saving does not send a message.", MUTED, 3), BorderLayout.CENTER);
        frame.add(heading, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        add(form, label("", MUTED), 0, 0, 0);
        add(form, label("Name", MUTED), 1, 0, 0.3);
        add(form, label("Discord webhook URL · masked", MUTED), 2, 0, 0.7);
        List<JTextField> names = new ArrayList<>();
        List<JPasswordField> webhooks = new ArrayList<>();
        for (int i = 0; i < ROWS; i++)
        {
            JTextField name = new JTextField();
            JPasswordField webhook = new JPasswordField();
            webhook.setEchoChar('\u2022');
            style(name);
            style(webhook);
            name.setPreferredSize(new Dimension(145, 30));
            webhook.setPreferredSize(new Dimension(340, 30));
            name.getAccessibleContext().setAccessibleName("Destination " + (i + 1) + " name");
            webhook.getAccessibleContext().setAccessibleName("Destination " + (i + 1) + " webhook URL");
            name.setToolTipText("A unique display name, such as Clan or Personal");
            webhook.setToolTipText("Paste the HTTPS Discord webhook URL; its secret stays masked");
            add(form, label(Integer.toString(i + 1), MUTED), 0, i + 1, 0);
            add(form, name, 1, i + 1, 0.3);
            add(form, webhook, 2, i + 1, 0.7);
            names.add(name);
            webhooks.add(webhook);
        }
        center.add(form, BorderLayout.NORTH);
        JTextArea error = note("", ERROR, 3);
        center.add(error, BorderLayout.SOUTH);
        frame.add(center, BorderLayout.CENTER);
        try
        {
            List<Destination> existing = Destination.parse(currentValue);
            for (int i = 0; i < existing.size(); i++)
            {
                names.get(i).setText(existing.get(i).getName());
                webhooks.get(i).setText(existing.get(i).getUrl());
            }
        }
        catch (IllegalArgumentException ignored)
        {
            error.setText("Existing destinations could not be read. Cancel keeps them unchanged. Saving replaces them with the entries below.");
        }

        JPanel buttons = new JPanel(new GridLayout(1, 2, 8, 0));
        buttons.setOpaque(false);
        JButton cancel = button("Cancel", false);
        JButton save = button("Save destinations", true);
        cancel.addActionListener(e -> dialog.dispose());
        save.addActionListener(e ->
        {
            List<String> nameValues = new ArrayList<>();
            List<String> urlValues = new ArrayList<>();
            for (int i = 0; i < ROWS; i++)
            {
                nameValues.add(names.get(i).getText());
                char[] password = webhooks.get(i).getPassword();
                try { urlValues.add(new String(password)); }
                finally { Arrays.fill(password, '\0'); }
            }
            final String normalized;
            try { normalized = serialize(nameValues, urlValues); }
            catch (IllegalArgumentException invalid)
            {
                error.setText(invalid.getMessage());
                return;
            }
            try { onSave.accept(normalized); }
            catch (RuntimeException failure)
            {
                error.setText("The destinations could not be saved. Your entries are still here; try again or cancel.");
                return;
            }
            dialog.dispose();
        });
        buttons.add(cancel);
        buttons.add(save);
        frame.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(frame);
        dialog.getRootPane().setDefaultButton(save);
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "cancelEditor");
        dialog.getRootPane().getActionMap().put("cancelEditor", new AbstractAction()
        {
            @Override public void actionPerformed(ActionEvent event) { dialog.dispose(); }
        });
        dialog.addWindowListener(new WindowAdapter()
        {
            @Override public void windowClosed(WindowEvent event)
            {
                for (JPasswordField webhook : webhooks) { webhook.setText(""); }
            }
        });
        dialog.pack();
        dialog.setSize(620, dialog.getHeight());
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    /** Returns normalized multiline config, with errors that never contain a name or credential. */
    static String serialize(List<String> names, List<String> urls)
    {
        if (names == null || urls == null || names.size() != urls.size() || names.size() > ROWS)
        {
            throw new IllegalArgumentException("Use at most five named Discord destinations.");
        }
        Set<String> usedNames = new HashSet<>();
        Map<String, Integer> usedEndpoints = new HashMap<>();
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < names.size(); i++)
        {
            String name = names.get(i) == null ? "" : names.get(i).trim();
            String url = urls.get(i) == null ? "" : urls.get(i).trim();
            int row = i + 1;
            if (name.isEmpty() && url.isEmpty()) { continue; }
            if (name.isEmpty() || url.isEmpty())
            {
                throw new IllegalArgumentException("Row " + row + " needs both a name and a webhook URL.");
            }
            if (name.length() > 60 || name.contains("|") || name.contains("://") || name.chars().anyMatch(Character::isISOControl))
            {
                throw new IllegalArgumentException("Row " + row + " needs a name of 1–60 characters without URLs, line breaks or |.");
            }
            if (!usedNames.add(name.toLowerCase(Locale.ROOT)))
            {
                throw new IllegalArgumentException("Row " + row + " repeats a destination name. Give each destination a unique name.");
            }
            final Destination destination;
            try
            {
                if (url.chars().anyMatch(Character::isISOControl)) { throw new IllegalArgumentException(); }
                List<Destination> parsed = Destination.parse(name + " | " + url);
                if (parsed.size() != 1) { throw new IllegalArgumentException(); }
                destination = parsed.get(0);
            }
            catch (IllegalArgumentException invalid)
            {
                throw new IllegalArgumentException("Row " + row + " needs a valid HTTPS Discord webhook URL.");
            }
            Integer previous = usedEndpoints.putIfAbsent(destination.getId(), row);
            if (previous != null)
            {
                throw new IllegalArgumentException("Rows " + previous + " and " + row + " use the same webhook. Keep one name for each destination.");
            }
            lines.add(destination.getName() + " | " + destination.getUrl());
        }
        return String.join("\n", lines);
    }

    private static void add(JPanel form, Component component, int column, int row, double weight)
    {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column;
        constraints.gridy = row;
        constraints.weightx = weight;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(4, 0, 4, column == 2 ? 0 : 9);
        form.add(component, constraints);
    }

    private static JLabel label(String value, Color color)
    {
        JLabel label = new JLabel(value);
        label.putClientProperty("html.disable", true);
        label.setFont(BODY);
        label.setForeground(color);
        return label;
    }

    private static JTextArea note(String value, Color color, int rows)
    {
        JTextArea text = new JTextArea(value, rows, 46);
        text.setFont(BODY);
        text.setForeground(color);
        text.setOpaque(false);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setBorder(BorderFactory.createEmptyBorder());
        return text;
    }

    private static void style(JTextField field)
    {
        field.setFont(BODY);
        field.setBackground(FIELD);
        field.setForeground(TEXT);
        field.setCaretColor(TEXT);
        field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(64, 69, 77)),
            BorderFactory.createEmptyBorder(5, 6, 5, 6)));
    }

    private static JButton button(String title, boolean primary)
    {
        JButton button = new JButton(title);
        button.setFont(BODY);
        button.setForeground(primary ? BACKGROUND : TEXT);
        button.setBackground(primary ? GOLD : FIELD);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        return button;
    }
}
