package zf.nastest.controller;

import zf.nastest.entity.TestList;

import java.util.ArrayList;
import java.util.List;

public class listtest {

    public static void main(String[] args) {

        List<TestList> lists = new ArrayList<>();
        TestList list1 = new TestList();
        list1.setId(1);
        list1.setName("test1");
        lists.add(list1);
        TestList list2 = new TestList();
        list2.setId(2);
        list2.setName("test2");
        lists.add(list2);
        for (TestList list : lists) {
            list.setName("aaa");
        }
//        System.out.println(lists.get(0).getName());

       int m = lists.stream().mapToInt(TestList::getId).sum();
    }
}
