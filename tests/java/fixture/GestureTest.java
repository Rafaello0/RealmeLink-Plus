package fixture;
import app.hxreborn.extension.realmelink.ModeKeeper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class GestureTest {
 public static final class Action {
  final String type;
  List<String> options;
  public Action(String type,List<String> options){this.type=type;this.options=options;}
  public String getActionType(){return type;}
  public List<String> getOptionValues(){return options;}
  public void setOptionValues(List<String> options){this.options=options;}
  public Map<String,String> getOptionSupportVersions(){return null;}
 }
 public static final class Row {
  final String type; final Action action;
  List<Row> items=new ArrayList<>();
  String min;
  public Row(String type,Action action){this.type=type;this.action=action;}
  public String getItemType(){return type;}
  public Action getKeyAction(){return action;}
  public List<Row> getSubItems(){return items;}
  public void setSubItems(List<Row> items){this.items=items;}
  public String getMinSupportVersion(){return min;}
  public String getMinUnSupportVersion(){return null;}
  public List<String> getSupportRegions(){return null;}
 }
 private static Row row(String type,String... options){return new Row(type,new Action(type,Arrays.asList(options)));}
 private static void check(boolean v,String text){if(!v)throw new IllegalStateException(text);}
 public static void run(){
  Row group=new Row("GROUP_KEY_LEFT",null);
  Row dbl=row("ITEM_ACTION_DOUBLE_CLICK","VALUE_PLAY_PAUSE","VALUE_OFF");
  group.items.add(dbl);
  ModeKeeper.exposeSingleTap(group);
  check(group.items.size()==2 && "ITEM_ACTION_SINGLE_CLICK".equals(group.items.get(0).type),"single tap row");
  check(group.items.get(0).action.options.equals(Arrays.asList("VALUE_PLAY_PAUSE","VALUE_OFF")),"single tap options");
  ModeKeeper.exposeSingleTap(group);
  check(group.items.size()==2 && group.items.get(1)==dbl,"idempotent, existing row intact");
  Row off=new Row("GROUP_KEY_RIGHT",null);
  off.items.add(dbl);off.items.add(row("ITEM_ACTION_SINGLE_CLICK","VALUE_OFF"));
  ModeKeeper.exposeSingleTap(off);
  check(off.items.get(1).action.options.size()==2,"Off-only row expanded");
  Row restricted=new Row("GROUP_KEY_LEFT",null);
  Row limited=row("ITEM_ACTION_DOUBLE_CLICK","VALUE_PLAY_PAUSE","VALUE_OFF");limited.min="1.0";
  restricted.items.add(limited);ModeKeeper.exposeSingleTap(restricted);
  check(restricted.items.size()==1,"firmware restriction preserved");
  Row other=new Row("GROUP_KEY_NOISE",null);other.items.add(dbl);ModeKeeper.exposeSingleTap(other);
  check(other.items.size()==1,"unrelated group preserved");
  ModeKeeper.exposeSingleTap(null);
 }
}
