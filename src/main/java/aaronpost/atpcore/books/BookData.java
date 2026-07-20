package aaronpost.atpcore.books;

import aaronpost.atpcore.gui.GUIUtil;
import aaronpost.atpcore.gui.IDisplayable;
import aaronpost.atpcore.registries.IDataContainer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BookData implements IDataContainer, IDisplayable {
    public String[] pages;
    public String name;
    public String author = "Aaronn";
    public LocalDate date;
    public ChatColor color;
    public enum BookType { NEWS, TIP }
    public String[] shortDescription;
    public BookType type;
    @Override
    public void validate() {
        if(shortDescription == null) {shortDescription=new String[]{};}
    }

    @Override
    public String getName() {
        return name;
    }

    public ItemStack getBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        meta.setTitle(getDisplayName());
        meta.setAuthor(author);

        for (String page : pages) {
            BaseComponent[] components = parseString(page);
            meta.spigot().addPage(components);
        }
        book.setItemMeta(meta);
        return book;
    }


        private static final Pattern LINK_PATTERN = Pattern.compile("<a>(.+?)</a>");

        public static BaseComponent[] parseString(String input) {
            List<BaseComponent> components = new ArrayList<>();

            int lastIndex = 0;
            Matcher matcher = LINK_PATTERN.matcher(input);

            while (matcher.find()) {
                // Text before the link
                String beforeLink = input.substring(lastIndex, matcher.start());
                if (!beforeLink.isEmpty()) {
                    components.addAll(parseColors(beforeLink));
                }

                // The link itself
                String url = matcher.group(1);
                TextComponent linkComp = new TextComponent(url);
                linkComp.setColor(net.md_5.bungee.api.ChatColor.BLUE); // optional color
                linkComp.setUnderlined(true);
                linkComp.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
                components.add(linkComp);

                lastIndex = matcher.end();
            }

            // Text after the last link
            if (lastIndex < input.length()) {
                String remaining = input.substring(lastIndex);
                components.addAll(parseColors(remaining));
            }

            return components.toArray(new BaseComponent[0]);
        }

        public static List<BaseComponent> parseColors(String text) {
            List<BaseComponent> components = new ArrayList<>();

            // Replace & codes with ChatColor
            String[] parts = text.split("(?=&)");
            ChatColor currentColor = ChatColor.RESET;
            boolean bold = false;
            boolean italic = false;
            boolean underline = false;
            boolean strikethrough = false;
            boolean magic = false;
            boolean reset = false;

            for (String part : parts) {
                if (part.isEmpty()) continue;

                if (part.startsWith("&")) {
                    char code = part.charAt(1);
                    ChatColor color = ChatColor.getByChar(code);

                    if (color != null) {
                        if (color.isColor()) currentColor = color;
                        if (color == ChatColor.BOLD) bold = true;
                        if (color == ChatColor.ITALIC) italic = true;
                        if (color == ChatColor.UNDERLINE) underline = true;
                        if (color == ChatColor.STRIKETHROUGH) strikethrough = true;
                        if (color == ChatColor.MAGIC) magic = true;
                        if (color == ChatColor.RESET) reset = true;
                    }

                    part = part.substring(2); // Remove color code
                }

                if (!part.isEmpty()) {
                    TextComponent comp = new TextComponent(part);
                    comp.setColor(currentColor.asBungee());
                    comp.setBold(bold);
                    comp.setItalic(italic);
                    comp.setUnderlined(underline);
                    comp.setStrikethrough(strikethrough);
                    comp.setObfuscated(magic);
                    components.add(comp);
                }
            }

            return components;
        }

    /** Translate {@code &}-prefixed colour codes; null-safe ("" for null). */
    public static String colorize(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }



    @Override
    public ItemStack getItemStack() {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GRAY + "" + ChatColor.ITALIC + date);
        for(String line: shortDescription) {
            lines.add(ChatColor.GRAY + line);
        }
        return GUIUtil.attachNameAndLore(
                new ItemStack(Material.BOOK),
                getDisplayName(),
                GUIUtil.prettyIndent(lines,3)
        );
    }

    @Override
    public String getDisplayName() {
        return color + name;
    }
}
